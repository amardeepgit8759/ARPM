package com.placefy.infrastructure.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.placefy.domain.question.BankCoverageReport;
import com.placefy.domain.question.QuestionBank;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.support.RepositoryPaths;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The CI gate for question content.
 *
 * <p>Runs against the real files in {@code content/questions} on every {@code ./gradlew test}, in
 * the fast suite, without Docker. A malformed question fails the ordinary build rather than
 * waiting for someone to remember a lint step.
 *
 * <p>The bank ships empty — Placefy's rules forbid inventing question content — so most of what
 * this asserts today is that the pipeline is wired and guarding the directory. It starts earning
 * its keep the moment the first real question lands.
 */
class QuestionContentValidationTest {

    private static final double NEAR_DUPLICATE_THRESHOLD = 0.8;
    private static final Instant AT = Instant.parse("2026-09-22T09:00:00Z");

    private final QuestionContentLoader loader = new QuestionContentLoader();

    private static Path questionContent() {
        return RepositoryPaths.resolve("content/questions");
    }

    private static TaxonomyVersion shippedTaxonomy() {
        return new TaxonomyContentLoader()
                .load(RepositoryPaths.taxonomyContent(), TaxonomyVersionId.of(UUID.randomUUID()), AT);
    }

    @Test
    @DisplayName("the shipped question content is valid")
    void shippedContentLoads() {
        assertThatCode(this::load).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the bank declares the taxonomy version it was written against, and they agree")
    void bankAndTaxonomyAgreeOnVersion() {
        assertThat(load().taxonomyVersion()).isEqualTo(shippedTaxonomy().label().value());
    }

    @Test
    @DisplayName("an empty bank is valid; the pipeline guards the directory before content arrives")
    void anEmptyBankIsAcceptable() {
        assertThat(load().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("the authoring template exists, or the documented workflow is broken")
    void theTemplateIsPresent() {
        assertThat(questionContent().resolve("banks/_TEMPLATE.yaml")).exists();
        assertThat(questionContent().resolve("SCHEMA.md")).exists();

        // The template is full of REPLACE_ME values and would never validate. Loading succeeding
        // is the assertion that underscore-prefixed files are skipped.
        assertThatCode(this::load).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("coverage is reported against the whole taxonomy, so every gap in the bank is visible")
    void coverageReportsEverySubSkill() {
        TaxonomyVersion taxonomy = shippedTaxonomy();
        BankCoverageReport report = BankCoverageReport.of(load(), taxonomy);

        assertThat(report.totalSubSkills()).isEqualTo(taxonomy.subSkillCount());
        // Every sub-skill is currently uncovered, which is the honest state of an empty bank and
        // exactly what an author needs to see.
        assertThat(report.uncovered()).hasSize(taxonomy.subSkillCount());
        assertThat(report.rows())
                .allSatisfy(row -> assertThat(row.countsByDifficulty()).hasSize(5));
    }

    private QuestionBank load() {
        return loader.load(questionContent(), shippedTaxonomy(), NEAR_DUPLICATE_THRESHOLD);
    }
}
