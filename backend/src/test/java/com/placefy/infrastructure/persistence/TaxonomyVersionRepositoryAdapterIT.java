package com.placefy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.TaxonomyLabelAlreadyUsedException;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyStatus;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import com.placefy.infrastructure.content.TaxonomyContentLoader;
import com.placefy.support.PostgresIntegrationTest;
import com.placefy.support.RepositoryPaths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Round-trips the real shipped taxonomy through PostgreSQL.
 *
 * <p>Using the actual content rather than a toy fixture is deliberate: it exercises a tree with
 * cross-domain prerequisites, multi-prerequisite sub-skills and roughly forty codes, which is
 * where ordering and edge-resolution bugs actually live.
 */
class TaxonomyVersionRepositoryAdapterIT extends PostgresIntegrationTest {

    private static final Instant IMPORTED_AT = Instant.parse("2026-09-21T09:00:00Z");

    @Autowired
    private TaxonomyVersionRepository taxonomies;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearTaxonomyTables() {
        jdbc.execute("TRUNCATE TABLE taxonomy_versions CASCADE");
    }

    @Test
    @DisplayName("the shipped taxonomy survives a write-then-read unchanged")
    void roundTripsTheShippedTaxonomy() {
        TaxonomyVersion original = shippedTaxonomy("2026.1-roundtrip");

        taxonomies.insert(original);
        TaxonomyVersion loaded = taxonomies.findByLabel(original.label()).orElseThrow();

        assertThat(loaded.label()).isEqualTo(original.label());
        assertThat(loaded.status()).isEqualTo(TaxonomyStatus.DRAFT);
        assertThat(loaded.sourceChecksum()).isEqualTo(original.sourceChecksum());
        assertThat(loaded.importedAt()).isEqualTo(IMPORTED_AT);
        assertThat(loaded.subSkillCount()).isEqualTo(original.subSkillCount());
        assertThat(loaded.domains()).isEqualTo(original.domains());
    }

    @Test
    @DisplayName("declaration order is preserved through storage, not left to the database")
    void preservesOrdering() {
        TaxonomyVersion original = shippedTaxonomy("2026.1-order");
        taxonomies.insert(original);

        TaxonomyVersion loaded = taxonomies.findByLabel(original.label()).orElseThrow();

        assertThat(loaded.domains().stream().map(domain -> domain.code().value()))
                .containsExactlyElementsOf(
                        original.domains().stream().map(domain -> domain.code().value()).toList());
        assertThat(loaded.allSubSkills().stream().map(subSkill -> subSkill.code().value()))
                .containsExactlyElementsOf(original.allSubSkills().stream()
                        .map(subSkill -> subSkill.code().value())
                        .toList());
    }

    @Test
    @DisplayName("prerequisite edges survive, including the cross-domain one and their order")
    void preservesPrerequisites() {
        TaxonomyVersion original = shippedTaxonomy("2026.1-prereqs");
        taxonomies.insert(original);

        TaxonomyVersion loaded = taxonomies.findByLabel(original.label()).orElseThrow();

        SubSkill indexFundamentals =
                loaded.findSubSkill(SubSkillCode.of("index-fundamentals")).orElseThrow();
        assertThat(indexFundamentals.prerequisites())
                .containsExactly(
                        SubSkillCode.of("sql-select-fundamentals"), SubSkillCode.of("balanced-tree-concepts"));

        for (SubSkill subSkill : original.allSubSkills()) {
            assertThat(loaded.findSubSkill(subSkill.code()).orElseThrow().prerequisites())
                    .as("prerequisites of %s", subSkill.code())
                    .isEqualTo(subSkill.prerequisites());
        }
    }

    @Test
    @DisplayName("a version whose content came back invalid would fail to rehydrate, so a clean read proves the tree")
    void rehydrationRevalidatesTheWholeGraph() {
        TaxonomyVersion original = shippedTaxonomy("2026.1-revalidate");
        taxonomies.insert(original);

        // rehydrate() runs the same duplicate/unknown-reference/cycle checks as import. If the
        // adapter dropped or mis-wired an edge, this read would throw rather than quietly differ.
        assertThat(taxonomies.findByLabel(original.label())).isPresent();
    }

    @Test
    void reportsAbsenceForAnUnknownLabel() {
        assertThat(taxonomies.findByLabel(TaxonomyVersionLabel.of("never-imported"))).isEmpty();
        assertThat(taxonomies.existsByLabel(TaxonomyVersionLabel.of("never-imported"))).isFalse();
    }

    @Test
    @DisplayName("the unique index surfaces as a typed exception, not a raw 500")
    void translatesTheLabelUniqueIndexViolation() {
        TaxonomyVersion first = shippedTaxonomy("2026.1-clash");
        taxonomies.insert(first);

        TaxonomyVersion second = shippedTaxonomy("2026.1-clash");

        assertThatThrownBy(() -> taxonomies.insert(second))
                .isInstanceOf(TaxonomyLabelAlreadyUsedException.class);
    }

    @Test
    void publishingPersistsTheStatusAndInstant() {
        TaxonomyVersion draft = shippedTaxonomy("2026.1-publish");
        taxonomies.insert(draft);

        Instant publishedAt = IMPORTED_AT.plusSeconds(600);
        taxonomies.markPublished(draft.publish(publishedAt));

        TaxonomyVersion loaded = taxonomies.findByLabel(draft.label()).orElseThrow();
        assertThat(loaded.isPublished()).isTrue();
        assertThat(loaded.publishedAt()).contains(publishedAt);
    }

    @Test
    @DisplayName("publishing touches no content row")
    void publishingLeavesContentUntouched() {
        TaxonomyVersion draft = shippedTaxonomy("2026.1-immutable");
        taxonomies.insert(draft);
        List<Domain> before = taxonomies.findByLabel(draft.label()).orElseThrow().domains();

        taxonomies.markPublished(draft.publish(IMPORTED_AT.plusSeconds(60)));

        assertThat(taxonomies.findByLabel(draft.label()).orElseThrow().domains()).isEqualTo(before);
    }

    @Test
    @DisplayName("two versions coexist without their globally-unique codes colliding")
    void twoVersionsCoexist() {
        taxonomies.insert(shippedTaxonomy("2026.1-first"));
        taxonomies.insert(shippedTaxonomy("2026.2-second"));

        // Codes are unique per version, not per table: the same taxonomy imported twice under
        // different labels must not trip the uniqueness constraints.
        assertThat(taxonomies.findByLabel(TaxonomyVersionLabel.of("2026.1-first"))).isPresent();
        assertThat(taxonomies.findByLabel(TaxonomyVersionLabel.of("2026.2-second"))).isPresent();

        Integer versions = jdbc.queryForObject("SELECT COUNT(*) FROM taxonomy_versions", Integer.class);
        assertThat(versions).isEqualTo(2);
    }

    @Test
    @DisplayName("deleting a version takes its whole content tree with it")
    void contentCascadesWithItsVersion() {
        TaxonomyVersion version = shippedTaxonomy("2026.1-cascade");
        taxonomies.insert(version);

        jdbc.update("DELETE FROM taxonomy_versions WHERE label = ?", version.label().value());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM taxonomy_domains", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM taxonomy_sub_skills", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM taxonomy_sub_skill_prerequisites", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("the database refuses a self-prerequisite even if application code ever forgot")
    void theSchemaRejectsASelfPrerequisite() {
        TaxonomyVersion version = shippedTaxonomy("2026.1-selfedge");
        taxonomies.insert(version);

        UUID anySubSkill = jdbc.queryForObject(
                "SELECT id FROM taxonomy_sub_skills LIMIT 1", UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO taxonomy_sub_skill_prerequisites "
                                + "(id, sub_skill_id, prerequisite_sub_skill_id, ordinal) VALUES (?, ?, ?, 0)",
                        UUID.randomUUID(),
                        anySubSkill,
                        anySubSkill))
                .hasMessageContaining("taxonomy_prerequisites_not_self");
    }

    @Test
    @DisplayName("the schema refuses a published row with no published_at")
    void theSchemaRejectsInconsistentPublishState() {
        TaxonomyVersion version = shippedTaxonomy("2026.1-badstate");
        taxonomies.insert(version);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE taxonomy_versions SET status = 'PUBLISHED' WHERE label = ?",
                        version.label().value()))
                .hasMessageContaining("taxonomy_versions_published_consistent");
    }

    /** Loads the real content/taxonomy files under a test-specific label. */
    private TaxonomyVersion shippedTaxonomy(String label) {
        TaxonomyVersion loaded = new TaxonomyContentLoader()
                .load(
                        RepositoryPaths.taxonomyContent(),
                        TaxonomyVersionId.of(UUID.randomUUID()),
                        IMPORTED_AT);

        return TaxonomyVersion.rehydrate(
                loaded.id(),
                TaxonomyVersionLabel.of(label),
                loaded.status(),
                loaded.sourceChecksum(),
                loaded.importedAt(),
                loaded.publishedAt().orElse(null),
                loaded.domains());
    }

    @Test
    @DisplayName("the shipped content is big enough for this test to mean something")
    void theFixtureIsNotTrivial() {
        TaxonomyVersion taxonomy = shippedTaxonomy("2026.1-size");

        assertThat(taxonomy.domains()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(taxonomy.subSkillCount()).isGreaterThanOrEqualTo(30);
        // Most of the round-trip risk is in the edge table, so the fixture has to have edges.
        assertThat(taxonomy.allSubSkills().stream().filter(SubSkill::hasPrerequisites).count())
                .isGreaterThanOrEqualTo(20);
    }
}
