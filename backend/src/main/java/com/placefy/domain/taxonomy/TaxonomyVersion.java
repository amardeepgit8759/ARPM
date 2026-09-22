package com.placefy.domain.taxonomy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One complete, validated, immutable taxonomy.
 *
 * <p>This is the aggregate root, and the only place a taxonomy can be declared valid. Node
 * records enforce their own field shapes; everything that spans more than one node — global
 * code uniqueness, prerequisite resolution, acyclicity — is enforced here, because only here
 * is the whole graph visible.
 *
 * <p>Validation collects every violation rather than throwing on the first, and iterates in
 * declaration order throughout, so the same content always produces the same report in the
 * same sequence. A validator whose output depends on hash ordering is not much of a gate.
 */
public final class TaxonomyVersion {

    private final TaxonomyVersionId id;
    private final TaxonomyVersionLabel label;
    private final TaxonomyStatus status;
    private final ContentChecksum sourceChecksum;
    private final Instant importedAt;
    private final Instant publishedAt;
    private final List<Domain> domains;

    /** Built once at construction: every lookup downstream is by sub-skill code. */
    private final Map<SubSkillCode, SubSkill> subSkillsByCode;

    private TaxonomyVersion(
            TaxonomyVersionId id,
            TaxonomyVersionLabel label,
            TaxonomyStatus status,
            ContentChecksum sourceChecksum,
            Instant importedAt,
            Instant publishedAt,
            List<Domain> domains) {

        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.status = Objects.requireNonNull(status, "status");
        this.sourceChecksum = Objects.requireNonNull(sourceChecksum, "sourceChecksum");
        this.importedAt = Objects.requireNonNull(importedAt, "importedAt");
        this.publishedAt = publishedAt;
        this.domains = List.copyOf(Objects.requireNonNull(domains, "domains"));

        List<TaxonomyViolation> violations = validate(this.domains);
        if (!violations.isEmpty()) {
            throw new InvalidTaxonomyException(violations);
        }

        Map<SubSkillCode, SubSkill> index = new LinkedHashMap<>();
        for (Domain domain : this.domains) {
            for (Skill skill : domain.skills()) {
                for (SubSkill subSkill : skill.subSkills()) {
                    index.put(subSkill.code(), subSkill);
                }
            }
        }
        this.subSkillsByCode = Map.copyOf(index);
    }

    /** Creates a DRAFT version from freshly loaded content. */
    public static TaxonomyVersion importedFrom(
            TaxonomyVersionId id,
            TaxonomyVersionLabel label,
            ContentChecksum sourceChecksum,
            Instant importedAt,
            List<Domain> domains) {
        return new TaxonomyVersion(id, label, TaxonomyStatus.DRAFT, sourceChecksum, importedAt, null, domains);
    }

    /** Rebuilds a version already in storage. Used only by persistence mappers. */
    public static TaxonomyVersion rehydrate(
            TaxonomyVersionId id,
            TaxonomyVersionLabel label,
            TaxonomyStatus status,
            ContentChecksum sourceChecksum,
            Instant importedAt,
            Instant publishedAt,
            List<Domain> domains) {
        return new TaxonomyVersion(id, label, status, sourceChecksum, importedAt, publishedAt, domains);
    }

    /**
     * Puts this version into circulation.
     *
     * <p>Not idempotent on purpose. Re-publishing would move {@code publishedAt} on a version
     * that other records already reference, and silently rewriting the history of something
     * declared immutable is exactly the failure this status exists to prevent.
     */
    public TaxonomyVersion publish(Instant publishedAt) {
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (status == TaxonomyStatus.PUBLISHED) {
            throw new TaxonomyAlreadyPublishedException(label);
        }
        if (publishedAt.isBefore(importedAt)) {
            throw new com.placefy.domain.DomainValidationException(
                    "publishedAt", "A taxonomy version cannot be published before it was imported.");
        }
        return new TaxonomyVersion(
                id, label, TaxonomyStatus.PUBLISHED, sourceChecksum, importedAt, publishedAt, domains);
    }

    // ------------------------------------------------------------------ validation

    private static List<TaxonomyViolation> validate(List<Domain> domains) {
        List<TaxonomyViolation> violations = new ArrayList<>();

        if (domains.isEmpty()) {
            violations.add(TaxonomyViolation.emptyTaxonomy());
            return violations;
        }

        Set<DomainCode> seenDomains = new LinkedHashSet<>();
        Set<SkillCode> seenSkills = new LinkedHashSet<>();
        Set<SubSkillCode> seenSubSkills = new LinkedHashSet<>();

        for (Domain domain : domains) {
            if (!seenDomains.add(domain.code())) {
                violations.add(TaxonomyViolation.duplicateDomainCode(domain.code()));
            }
            if (domain.skills().isEmpty()) {
                violations.add(TaxonomyViolation.emptyDomain(domain.code()));
            }
            for (Skill skill : domain.skills()) {
                if (!seenSkills.add(skill.code())) {
                    violations.add(TaxonomyViolation.duplicateSkillCode(skill.code()));
                }
                if (skill.subSkills().isEmpty()) {
                    violations.add(TaxonomyViolation.emptySkill(domain.code(), skill.code()));
                }
                for (SubSkill subSkill : skill.subSkills()) {
                    if (!seenSubSkills.add(subSkill.code())) {
                        violations.add(TaxonomyViolation.duplicateSubSkillCode(subSkill.code()));
                    }
                }
            }
        }

        Map<SubSkillCode, List<SubSkillCode>> graph =
                validatePrerequisites(domains, seenSubSkills, violations);
        detectCycles(graph, violations);

        return violations;
    }

    /**
     * Checks every prerequisite reference and returns the graph built from the ones that
     * resolved. Unresolved references are excluded so that cycle detection runs on a graph that
     * actually exists — otherwise a typo would be reported twice, once as unknown and again as
     * a spurious structural failure.
     */
    private static Map<SubSkillCode, List<SubSkillCode>> validatePrerequisites(
            List<Domain> domains, Set<SubSkillCode> known, List<TaxonomyViolation> violations) {

        Map<SubSkillCode, List<SubSkillCode>> graph = new LinkedHashMap<>();

        for (Domain domain : domains) {
            for (Skill skill : domain.skills()) {
                for (SubSkill subSkill : skill.subSkills()) {
                    List<SubSkillCode> resolved = new ArrayList<>();
                    Set<SubSkillCode> seenHere = new LinkedHashSet<>();

                    for (SubSkillCode prerequisite : subSkill.prerequisites()) {
                        if (prerequisite.equals(subSkill.code())) {
                            violations.add(TaxonomyViolation.selfPrerequisite(subSkill.code()));
                            continue;
                        }
                        if (!seenHere.add(prerequisite)) {
                            violations.add(TaxonomyViolation.duplicatePrerequisite(subSkill.code(), prerequisite));
                            continue;
                        }
                        if (!known.contains(prerequisite)) {
                            violations.add(TaxonomyViolation.unknownPrerequisite(subSkill.code(), prerequisite));
                            continue;
                        }
                        resolved.add(prerequisite);
                    }

                    graph.put(subSkill.code(), resolved);
                }
            }
        }

        return graph;
    }

    private static void detectCycles(
            Map<SubSkillCode, List<SubSkillCode>> graph, List<TaxonomyViolation> violations) {

        Map<SubSkillCode, Mark> marks = new LinkedHashMap<>();
        for (SubSkillCode start : graph.keySet()) {
            if (!marks.containsKey(start)) {
                walk(start, graph, marks, new ArrayList<>(), violations);
            }
        }
    }

    private static void walk(
            SubSkillCode node,
            Map<SubSkillCode, List<SubSkillCode>> graph,
            Map<SubSkillCode, Mark> marks,
            List<SubSkillCode> path,
            List<TaxonomyViolation> violations) {

        marks.put(node, Mark.VISITING);
        path.add(node);

        for (SubSkillCode next : graph.getOrDefault(node, List.of())) {
            Mark mark = marks.get(next);
            if (mark == null) {
                walk(next, graph, marks, path, violations);
            } else if (mark == Mark.VISITING) {
                // A back edge into the current path: everything from `next` onwards is the cycle.
                List<SubSkillCode> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
                cycle.add(next);
                violations.add(TaxonomyViolation.prerequisiteCycle(cycle));
            }
        }

        path.remove(path.size() - 1);
        marks.put(node, Mark.DONE);
    }

    private enum Mark {
        VISITING,
        DONE
    }

    // ------------------------------------------------------------------ accessors

    public TaxonomyVersionId id() {
        return id;
    }

    public TaxonomyVersionLabel label() {
        return label;
    }

    public TaxonomyStatus status() {
        return status;
    }

    public boolean isPublished() {
        return status == TaxonomyStatus.PUBLISHED;
    }

    public ContentChecksum sourceChecksum() {
        return sourceChecksum;
    }

    public Instant importedAt() {
        return importedAt;
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }

    public List<Domain> domains() {
        return domains;
    }

    public Optional<SubSkill> findSubSkill(SubSkillCode code) {
        return Optional.ofNullable(subSkillsByCode.get(code));
    }

    /** Every sub-skill in declaration order. */
    public Collection<SubSkill> allSubSkills() {
        List<SubSkill> all = new ArrayList<>();
        for (Domain domain : domains) {
            for (Skill skill : domain.skills()) {
                all.addAll(skill.subSkills());
            }
        }
        return List.copyOf(all);
    }

    public int subSkillCount() {
        return subSkillsByCode.size();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TaxonomyVersion that && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TaxonomyVersion[label=" + label + ", status=" + status + ", subSkills=" + subSkillCount() + "]";
    }
}
