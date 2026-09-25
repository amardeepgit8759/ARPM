package com.placefy.domain.question;

import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.DomainCode;
import com.placefy.domain.taxonomy.EffortHours;
import com.placefy.domain.taxonomy.NodeName;
import com.placefy.domain.taxonomy.Skill;
import com.placefy.domain.taxonomy.SkillCode;
import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Synthetic questions for exercising the validator.
 *
 * <p>Deliberately trivial arithmetic rather than anything resembling real assessment content.
 * These are fixtures for testing rules, not questions: Placefy's working rules forbid inventing
 * question content, and anything plausible here would eventually be mistaken for a seed bank.
 */
final class QuestionFixtures {

    static final String TAXONOMY_VERSION = "test-taxonomy-1";
    static final double NEAR_DUPLICATE_THRESHOLD = 0.8;

    private QuestionFixtures() {}

    static TaxonomyVersion taxonomy() {
        return TaxonomyVersion.importedFrom(
                TaxonomyVersionId.of(UUID.fromString("00000000-0000-0000-0000-0000000000aa")),
                TaxonomyVersionLabel.of(TAXONOMY_VERSION),
                com.placefy.domain.taxonomy.ContentChecksum.of("b".repeat(64)),
                Instant.parse("2026-09-22T09:00:00Z"),
                List.of(Domain.of(
                        DomainCode.of("arithmetic"),
                        NodeName.of("Arithmetic"),
                        List.of(Skill.of(
                                SkillCode.of("addition"),
                                NodeName.of("Addition"),
                                List.of(
                                        SubSkill.of(
                                                SubSkillCode.of("adding-integers"),
                                                NodeName.of("Adding Integers"),
                                                EffortHours.of(1),
                                                List.of()),
                                        SubSkill.of(
                                                SubSkillCode.of("adding-fractions"),
                                                NodeName.of("Adding Fractions"),
                                                EffortHours.of(2),
                                                List.of())))))));
    }

    static OriginalityAttestation originality() {
        return new OriginalityAttestation(
                "Author One", LocalDate.parse("2026-09-01"), true, "Written from scratch for this test.");
    }

    static ReviewerSignOff review() {
        return new ReviewerSignOff(
                "Reviewer Two", LocalDate.parse("2026-09-02"), ReviewOutcome.APPROVED, "Checked the key.");
    }

    static Question question(String code, String stem) {
        return question(code, stem, 3, "adding-integers");
    }

    static Question question(String code, String stem, int difficulty, String subSkill) {
        return new Question(
                QuestionCode.of(code),
                QuestionType.SINGLE_CHOICE,
                Difficulty.of(difficulty),
                stem,
                List.of(QuestionOption.of("a", "Four"), QuestionOption.of("b", "Five")),
                List.of("a"),
                "Two plus two is four. This is a fixture, not teaching material.",
                List.of(SubSkillTag.of(SubSkillCode.of(subSkill), BigDecimal.ONE)),
                originality(),
                review());
    }

    static QuestionBank bank(Question... questions) {
        return QuestionBank.of(
                "test-bank-1",
                TAXONOMY_VERSION,
                List.of(questions),
                taxonomy(),
                NEAR_DUPLICATE_THRESHOLD);
    }
}
