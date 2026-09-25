package com.placefy.domain.question;

import static com.placefy.domain.question.QuestionFixtures.NEAR_DUPLICATE_THRESHOLD;
import static com.placefy.domain.question.QuestionFixtures.TAXONOMY_VERSION;
import static com.placefy.domain.question.QuestionFixtures.bank;
import static com.placefy.domain.question.QuestionFixtures.question;
import static com.placefy.domain.question.QuestionFixtures.taxonomy;
import static org.assertj.core.api.Assertions.assertThat;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BankCoverageReportTest {

    @Test
    @DisplayName("a sub-skill with no questions is a row of zeros, not an absent row")
    void showsUncoveredSubSkills() {
        BankCoverageReport report = BankCoverageReport.of(
                bank(question("one", "What is two plus two in this fixture?", 2, "adding-integers")),
                taxonomy());

        assertThat(report.rows()).hasSize(2);
        assertThat(report.uncovered())
                .extracting(row -> row.code().value())
                .containsExactly("adding-fractions");

        BankCoverageReport.SubSkillCoverage uncovered =
                report.forSubSkill(SubSkillCode.of("adding-fractions")).orElseThrow();
        assertThat(uncovered.total()).isZero();
        assertThat(uncovered.countsByDifficulty()).hasSize(5);
        assertThat(uncovered.emptyDifficulties()).hasSize(5);
    }

    @Test
    void countsByDifficulty() {
        BankCoverageReport report = BankCoverageReport.of(
                bank(
                        question("one", "What is two plus two in this fixture?", 2, "adding-integers"),
                        question("two", "How many sides does a hexagon have here?", 2, "adding-integers"),
                        question("three", "Which of these numbers is prime in this fixture?", 5, "adding-integers")),
                taxonomy());

        BankCoverageReport.SubSkillCoverage row =
                report.forSubSkill(SubSkillCode.of("adding-integers")).orElseThrow();

        assertThat(row.total()).isEqualTo(3);
        assertThat(row.countAt(Difficulty.of(2))).isEqualTo(2);
        assertThat(row.countAt(Difficulty.of(5))).isEqualTo(1);
        assertThat(row.countAt(Difficulty.of(3))).isZero();
        assertThat(row.emptyDifficulties()).extracting(Difficulty::level).containsExactly(1, 3, 4);
    }

    @Test
    @DisplayName("every taxonomy sub-skill appears even when the bank is empty")
    void coversTheWholeTaxonomyForAnEmptyBank() {
        BankCoverageReport report = BankCoverageReport.of(
                QuestionBank.of("empty", TAXONOMY_VERSION, List.of(), taxonomy(), NEAR_DUPLICATE_THRESHOLD),
                taxonomy());

        assertThat(report.totalSubSkills()).isEqualTo(2);
        assertThat(report.uncovered()).hasSize(2);
        assertThat(report.totalTagged()).isZero();
        assertThat(report.covered()).isEmpty();
    }

    @Test
    @DisplayName("a multi-tagged question counts once per sub-skill it claims to measure")
    void countsTagAttachmentsNotQuestions() {
        Question multiTagged = new Question(
                QuestionCode.of("multi-tagged"),
                QuestionType.SINGLE_CHOICE,
                Difficulty.of(3),
                "Which of these sums is correct in this fixture?",
                List.of(QuestionOption.of("a", "Four"), QuestionOption.of("b", "Five")),
                List.of("a"),
                "Fixture explanation with enough characters to pass.",
                List.of(
                        SubSkillTag.of(SubSkillCode.of("adding-integers"), java.math.BigDecimal.ONE),
                        SubSkillTag.of(SubSkillCode.of("adding-fractions"), java.math.BigDecimal.ONE)),
                QuestionFixtures.originality(),
                QuestionFixtures.review());

        BankCoverageReport report = BankCoverageReport.of(bank(multiTagged), taxonomy());

        assertThat(report.totalTagged()).isEqualTo(2);
        assertThat(report.uncovered()).isEmpty();
    }

    @Test
    void reportsTotalsAcrossTheBank() {
        BankCoverageReport report = BankCoverageReport.of(
                bank(
                        question("one", "What is two plus two in this fixture?", 1, "adding-integers"),
                        question("two", "How many sides does a hexagon have here?", 1, "adding-fractions")),
                taxonomy());

        assertThat(report.countAt(Difficulty.of(1))).isEqualTo(2);
        assertThat(report.countAt(Difficulty.of(2))).isZero();
        assertThat(report.bankVersion()).isEqualTo("test-bank-1");
        assertThat(report.taxonomyVersion()).isEqualTo(TAXONOMY_VERSION);
    }
}
