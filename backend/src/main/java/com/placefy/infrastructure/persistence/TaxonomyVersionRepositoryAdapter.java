package com.placefy.infrastructure.persistence;

import com.placefy.application.error.TaxonomyLabelAlreadyUsedException;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.domain.taxonomy.ContentChecksum;
import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.DomainCode;
import com.placefy.domain.taxonomy.EffortHours;
import com.placefy.domain.taxonomy.NodeName;
import com.placefy.domain.taxonomy.Skill;
import com.placefy.domain.taxonomy.SkillCode;
import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyStatus;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class TaxonomyVersionRepositoryAdapter implements TaxonomyVersionRepository {

    /** Must match the index name in V2__create_taxonomy.sql. */
    private static final String LABEL_UNIQUE_INDEX = "taxonomy_versions_label_key";

    private final TaxonomyVersionJpaRepository versions;
    private final TaxonomyDomainJpaRepository domains;
    private final TaxonomySkillJpaRepository skills;
    private final TaxonomySubSkillJpaRepository subSkills;
    private final TaxonomySubSkillPrerequisiteJpaRepository prerequisites;
    private final IdGenerator ids;

    TaxonomyVersionRepositoryAdapter(
            TaxonomyVersionJpaRepository versions,
            TaxonomyDomainJpaRepository domains,
            TaxonomySkillJpaRepository skills,
            TaxonomySubSkillJpaRepository subSkills,
            TaxonomySubSkillPrerequisiteJpaRepository prerequisites,
            IdGenerator ids) {
        this.versions = versions;
        this.domains = domains;
        this.skills = skills;
        this.subSkills = subSkills;
        this.prerequisites = prerequisites;
        this.ids = ids;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLabel(TaxonomyVersionLabel label) {
        return versions.existsByLabel(label.value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaxonomyVersion> findByLabel(TaxonomyVersionLabel label) {
        return versions.findByLabel(label.value()).map(this::loadTree);
    }

    @Override
    @Transactional
    public TaxonomyVersion insert(TaxonomyVersion version) {
        UUID versionId = version.id().value();

        try {
            versions.saveAndFlush(new TaxonomyVersionJpaEntity(
                    versionId,
                    version.label().value(),
                    version.status().name(),
                    version.sourceChecksum().value(),
                    version.importedAt(),
                    version.publishedAt().orElse(null)));
        } catch (DataIntegrityViolationException e) {
            if (violatesLabelUniqueIndex(e)) {
                throw new TaxonomyLabelAlreadyUsedException(version.label());
            }
            throw e;
        }

        // Sub-skill rows have to exist before prerequisite edges can point at them, so the two
        // passes below are ordered, not incidental.
        Map<SubSkillCode, UUID> subSkillIds = insertContent(version, versionId);
        insertPrerequisites(version, subSkillIds);

        return version;
    }

    private Map<SubSkillCode, UUID> insertContent(TaxonomyVersion version, UUID versionId) {
        Map<SubSkillCode, UUID> subSkillIds = new LinkedHashMap<>();

        List<TaxonomyDomainJpaEntity> domainRows = new ArrayList<>();
        List<TaxonomySkillJpaEntity> skillRows = new ArrayList<>();
        List<TaxonomySubSkillJpaEntity> subSkillRows = new ArrayList<>();

        int domainOrdinal = 0;
        for (Domain domain : version.domains()) {
            UUID domainId = ids.newId();
            domainRows.add(new TaxonomyDomainJpaEntity(
                    domainId, versionId, domain.code().value(), domain.name().value(), domainOrdinal++));

            int skillOrdinal = 0;
            for (Skill skill : domain.skills()) {
                UUID skillId = ids.newId();
                skillRows.add(new TaxonomySkillJpaEntity(
                        skillId,
                        versionId,
                        domainId,
                        skill.code().value(),
                        skill.name().value(),
                        skillOrdinal++));

                int subSkillOrdinal = 0;
                for (SubSkill subSkill : skill.subSkills()) {
                    UUID subSkillId = ids.newId();
                    subSkillIds.put(subSkill.code(), subSkillId);
                    subSkillRows.add(new TaxonomySubSkillJpaEntity(
                            subSkillId,
                            versionId,
                            skillId,
                            subSkill.code().value(),
                            subSkill.name().value(),
                            subSkill.defaultEffortHours().value(),
                            subSkillOrdinal++));
                }
            }
        }

        domains.saveAll(domainRows);
        skills.saveAll(skillRows);
        subSkills.saveAll(subSkillRows);
        subSkills.flush();

        return subSkillIds;
    }

    private void insertPrerequisites(TaxonomyVersion version, Map<SubSkillCode, UUID> subSkillIds) {
        List<TaxonomySubSkillPrerequisiteJpaEntity> edges = new ArrayList<>();

        for (SubSkill subSkill : version.allSubSkills()) {
            int ordinal = 0;
            for (SubSkillCode prerequisite : subSkill.prerequisites()) {
                edges.add(new TaxonomySubSkillPrerequisiteJpaEntity(
                        ids.newId(),
                        subSkillIds.get(subSkill.code()),
                        // Non-null because the aggregate already proved every prerequisite
                        // resolves to a declared sub-skill; a null here would be a bug in the
                        // aggregate, not bad content.
                        subSkillIds.get(prerequisite),
                        ordinal++));
            }
        }

        prerequisites.saveAll(edges);
        prerequisites.flush();
    }

    @Override
    @Transactional
    public TaxonomyVersion markPublished(TaxonomyVersion published) {
        TaxonomyVersionJpaEntity entity = versions
                .findById(published.id().value())
                .orElseThrow(() -> new IllegalStateException(
                        "Taxonomy version " + published.label() + " vanished between read and publish."));

        entity.markPublished(published.publishedAt().orElseThrow());
        versions.saveAndFlush(entity);

        return published;
    }

    /** Four ordered queries, then assembly. No lazy loading, no surprises. */
    private TaxonomyVersion loadTree(TaxonomyVersionJpaEntity version) {
        UUID versionId = version.getId();

        List<TaxonomyDomainJpaEntity> domainRows = domains.findByTaxonomyVersionIdOrderByOrdinalAsc(versionId);
        List<TaxonomySkillJpaEntity> skillRows = skills.findByTaxonomyVersionIdOrderByOrdinalAsc(versionId);
        List<TaxonomySubSkillJpaEntity> subSkillRows =
                subSkills.findByTaxonomyVersionIdOrderByOrdinalAsc(versionId);

        Map<UUID, String> subSkillCodesById = new LinkedHashMap<>();
        subSkillRows.forEach(row -> subSkillCodesById.put(row.getId(), row.getCode()));

        Map<UUID, List<SubSkillCode>> prerequisitesBySubSkill = new LinkedHashMap<>();
        if (!subSkillCodesById.isEmpty()) {
            for (TaxonomySubSkillPrerequisiteJpaEntity edge :
                    prerequisites.findBySubSkillIdInOrderByOrdinalAsc(subSkillCodesById.keySet())) {
                prerequisitesBySubSkill
                        .computeIfAbsent(edge.getSubSkillId(), key -> new ArrayList<>())
                        .add(SubSkillCode.of(subSkillCodesById.get(edge.getPrerequisiteSubSkillId())));
            }
        }

        Map<UUID, List<SubSkill>> subSkillsBySkill = new LinkedHashMap<>();
        for (TaxonomySubSkillJpaEntity row : subSkillRows) {
            subSkillsBySkill
                    .computeIfAbsent(row.getTaxonomySkillId(), key -> new ArrayList<>())
                    .add(SubSkill.of(
                            SubSkillCode.of(row.getCode()),
                            NodeName.of(row.getName()),
                            EffortHours.of(row.getDefaultEffortHours()),
                            prerequisitesBySubSkill.getOrDefault(row.getId(), List.of())));
        }

        Map<UUID, List<Skill>> skillsByDomain = new LinkedHashMap<>();
        for (TaxonomySkillJpaEntity row : skillRows) {
            skillsByDomain
                    .computeIfAbsent(row.getTaxonomyDomainId(), key -> new ArrayList<>())
                    .add(Skill.of(
                            SkillCode.of(row.getCode()),
                            NodeName.of(row.getName()),
                            subSkillsBySkill.getOrDefault(row.getId(), List.of())));
        }

        List<Domain> assembled = domainRows.stream()
                .map(row -> Domain.of(
                        DomainCode.of(row.getCode()),
                        NodeName.of(row.getName()),
                        skillsByDomain.getOrDefault(row.getId(), List.of())))
                .toList();

        return TaxonomyVersion.rehydrate(
                TaxonomyVersionId.of(version.getId()),
                TaxonomyVersionLabel.of(version.getLabel()),
                TaxonomyStatus.valueOf(version.getStatus()),
                ContentChecksum.of(version.getSourceChecksum()),
                version.getImportedAt(),
                version.getPublishedAt(),
                assembled);
    }

    private boolean violatesLabelUniqueIndex(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return LABEL_UNIQUE_INDEX.equalsIgnoreCase(violation.getConstraintName());
            }
            cause = cause.getCause();
        }
        return false;
    }
}
