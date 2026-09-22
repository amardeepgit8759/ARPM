package com.placefy.infrastructure.content;

import static org.assertj.core.api.Assertions.assertThat;

import com.placefy.application.port.in.ImportTaxonomyVersion;
import com.placefy.application.port.in.PublishTaxonomyVersion;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.domain.taxonomy.TaxonomyStatus;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import com.placefy.support.PostgresIntegrationTest;
import com.placefy.support.RepositoryPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Exercises the startup seeding path against a real database.
 *
 * <p>The runner is driven directly rather than through {@code @SpringBootTest} properties. The
 * container is shared across test classes, so a runner that fired during context startup could
 * have its rows truncated by whichever other class happened to run next — the test would pass or
 * fail on ordering rather than on behaviour.
 */
class TaxonomySeedIT extends PostgresIntegrationTest {

    @Autowired
    private ImportTaxonomyVersion importTaxonomyVersion;

    @Autowired
    private PublishTaxonomyVersion publishTaxonomyVersion;

    @Autowired
    private TaxonomyVersionRepository taxonomies;

    @Autowired
    private IdGenerator ids;

    @Autowired
    private TimeProvider time;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearTaxonomyTables() {
        jdbc.execute("TRUNCATE TABLE taxonomy_versions CASCADE");
    }

    @Test
    @DisplayName("seeding imports and publishes the shipped content in one pass")
    void seedsAndPublishes() {
        runner(true).run(null);

        TaxonomyVersion seeded = taxonomies.findByLabel(shippedLabel()).orElseThrow();

        assertThat(seeded.status()).isEqualTo(TaxonomyStatus.PUBLISHED);
        assertThat(seeded.publishedAt()).isPresent();
        assertThat(seeded.domains())
                .extracting(domain -> domain.code().value())
                .containsExactly("data-structures-algorithms", "databases", "operating-systems");
        assertThat(seeded.subSkillCount()).isGreaterThanOrEqualTo(30);
    }

    @Test
    @DisplayName("seeding without publish leaves the version as a draft for a human to release")
    void importsWithoutPublishing() {
        runner(false).run(null);

        TaxonomyVersion seeded = taxonomies.findByLabel(shippedLabel()).orElseThrow();

        assertThat(seeded.status()).isEqualTo(TaxonomyStatus.DRAFT);
        assertThat(seeded.publishedAt()).isEmpty();
    }

    @Test
    @DisplayName("running the seed again writes nothing, so restarting a container is cheap and safe")
    void isIdempotentAcrossRestarts() {
        runner(true).run(null);
        TaxonomyVersion afterFirst = taxonomies.findByLabel(shippedLabel()).orElseThrow();

        runner(true).run(null);
        runner(true).run(null);

        TaxonomyVersion afterThird = taxonomies.findByLabel(shippedLabel()).orElseThrow();

        assertThat(afterThird.id()).isEqualTo(afterFirst.id());
        assertThat(afterThird.publishedAt()).isEqualTo(afterFirst.publishedAt());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM taxonomy_versions", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("re-seeding does not duplicate content rows")
    void doesNotDuplicateContentRows() {
        runner(true).run(null);
        Integer subSkillsAfterFirst =
                jdbc.queryForObject("SELECT COUNT(*) FROM taxonomy_sub_skills", Integer.class);
        Integer edgesAfterFirst = jdbc.queryForObject(
                "SELECT COUNT(*) FROM taxonomy_sub_skill_prerequisites", Integer.class);

        runner(true).run(null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM taxonomy_sub_skills", Integer.class))
                .isEqualTo(subSkillsAfterFirst);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM taxonomy_sub_skill_prerequisites", Integer.class))
                .isEqualTo(edgesAfterFirst);
    }

    @Test
    @DisplayName("the seeded taxonomy is queryable with its prerequisite graph intact")
    void seededContentIsFullyQueryable() {
        runner(true).run(null);

        TaxonomyVersion seeded = taxonomies.findByLabel(shippedLabel()).orElseThrow();

        assertThat(seeded.findSubSkill(com.placefy.domain.taxonomy.SubSkillCode.of("index-fundamentals"))
                        .orElseThrow()
                        .prerequisites())
                .extracting(com.placefy.domain.taxonomy.SubSkillCode::value)
                .containsExactly("sql-select-fundamentals", "balanced-tree-concepts");
    }

    private TaxonomySeedRunner runner(boolean publish) {
        return new TaxonomySeedRunner(
                new TaxonomySeedProperties(true, RepositoryPaths.taxonomyContent().toString(), publish),
                importTaxonomyVersion,
                publishTaxonomyVersion,
                ids,
                time);
    }

    /** The label declared in the shipped manifest, read rather than duplicated here. */
    private TaxonomyVersionLabel shippedLabel() {
        return new TaxonomyContentLoader()
                .load(
                        RepositoryPaths.taxonomyContent(),
                        com.placefy.domain.taxonomy.TaxonomyVersionId.of(ids.newId()),
                        time.now())
                .label();
    }
}
