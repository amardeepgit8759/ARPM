package com.placefy.domain.question;

import static com.placefy.domain.question.QuestionFixtures.originality;
import static com.placefy.domain.question.QuestionFixtures.question;
import static com.placefy.domain.question.QuestionFixtures.review;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestionTest {

    private static final String STEM = "What is two plus two in this fixture?";
    private static final String EXPLANATION = "Two plus two is four. Fixture text, not teaching material.";

    private Question build(
            QuestionType type,
            List<QuestionOption> options,
            List<String> correct,
            List<SubSkillTag> tags,
            OriginalityAttestation originality,
            ReviewerSignOff review) {
        return new Question(
                QuestionCode.of("fixture-question"),
                type,
                Difficulty.of(3),
                STEM,
                options,
                correct,
                EXPLANATION,
                tags,
                originality,
                review);
    }

    private static List<QuestionOption> twoOptions() {
        return List.of(QuestionOption.of("a", "Four"), QuestionOption.of("b", "Five"));
    }

    private static List<SubSkillTag> oneTag() {
        return List.of(SubSkillTag.of(SubSkillCode.of("adding-integers"), BigDecimal.ONE));
    }

    @Nested
    class Answers {

        @Test
        void acceptsAWellFormedSingleChoiceQuestion() {
            assertThatCode(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            twoOptions(),
                            List.of("a"),
                            oneTag(),
                            originality(),
                            review()))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsACorrectAnswerThatNamesNoOption() {
            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            twoOptions(),
                            List.of("z"),
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("no such option");
        }

        @Test
        void rejectsSingleChoiceWithTwoCorrectAnswers() {
            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            List.of(
                                    QuestionOption.of("a", "Four"),
                                    QuestionOption.of("b", "Five"),
                                    QuestionOption.of("c", "Six")),
                            List.of("a", "b"),
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("multiple choice with one answer should have been single choice")
        void rejectsMultipleChoiceWithOneCorrectAnswer() {
            assertThatThrownBy(() -> build(
                            QuestionType.MULTIPLE_CHOICE,
                            twoOptions(),
                            List.of("a"),
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("SINGLE_CHOICE");
        }

        @Test
        @DisplayName("a question where every option is correct asks nothing")
        void rejectsAllOptionsCorrect() {
            assertThatThrownBy(() -> build(
                            QuestionType.MULTIPLE_CHOICE,
                            twoOptions(),
                            List.of("a", "b"),
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("asks nothing");
        }

        @Test
        void rejectsFewerThanTwoOptions() {
            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            List.of(QuestionOption.of("a", "Four")),
                            List.of("a"),
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("two identically worded options make at least one unanswerable")
        void rejectsDuplicateOptionText() {
            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            List.of(QuestionOption.of("a", "Four"), QuestionOption.of("b", "four")),
                            List.of("a"),
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    class Tags {

        @Test
        @DisplayName("an untagged question could not move any score")
        void rejectsAQuestionWithNoTags() {
            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            twoOptions(),
                            List.of("a"),
                            List.of(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void rejectsANonPositiveWeight() {
            assertThatThrownBy(() -> SubSkillTag.of(SubSkillCode.of("adding-integers"), BigDecimal.ZERO))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        @DisplayName("weights are carried as written; nothing requires them to sum to one")
        void doesNotNormaliseWeights() {
            Question question = new Question(
                    QuestionCode.of("fixture-question"),
                    QuestionType.SINGLE_CHOICE,
                    Difficulty.of(3),
                    STEM,
                    twoOptions(),
                    List.of("a"),
                    EXPLANATION,
                    List.of(
                            SubSkillTag.of(SubSkillCode.of("adding-integers"), new BigDecimal("0.7")),
                            SubSkillTag.of(SubSkillCode.of("adding-fractions"), new BigDecimal("0.9"))),
                    originality(),
                    review());

            // Sums to 1.6. Whether that is allowed is docs/scoring-spec.md 5.2, still unanswered,
            // so the bank records what the author wrote rather than deciding it here.
            assertThat(question.tags()).extracting(SubSkillTag::weight)
                    .containsExactly(new BigDecimal("0.7"), new BigDecimal("0.9"));
        }
    }

    @Nested
    class Provenance {

        @Test
        @DisplayName("a question not attested as original cannot enter the bank")
        void rejectsNonOriginalWork() {
            OriginalityAttestation copied = new OriginalityAttestation(
                    "Author One", LocalDate.parse("2026-09-01"), false, "Taken from somewhere else.");

            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE, twoOptions(), List.of("a"), oneTag(), copied, review()))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("no approved route");
        }

        @Test
        @DisplayName("self-review is not review")
        void rejectsAuthorReviewingTheirOwnQuestion() {
            ReviewerSignOff selfReview = new ReviewerSignOff(
                    "Author One", LocalDate.parse("2026-09-02"), ReviewOutcome.APPROVED, "Looks fine to me.");

            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            twoOptions(),
                            List.of("a"),
                            oneTag(),
                            originality(),
                            selfReview))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("Self-review is not review");
        }

        @ParameterizedTest
        @ValueSource(strings = {"CHANGES_REQUESTED", "REJECTED"})
        void rejectsAnythingButApproval(String outcome) {
            ReviewerSignOff notApproved = new ReviewerSignOff(
                    "Reviewer Two",
                    LocalDate.parse("2026-09-02"),
                    ReviewOutcome.valueOf(outcome),
                    "Needs work.");

            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            twoOptions(),
                            List.of("a"),
                            oneTag(),
                            originality(),
                            notApproved))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void rejectsAReviewDatedBeforeTheQuestionWasWritten() {
            ReviewerSignOff early = new ReviewerSignOff(
                    "Reviewer Two", LocalDate.parse("2026-08-01"), ReviewOutcome.APPROVED, "Early.");

            assertThatThrownBy(() -> build(
                            QuestionType.SINGLE_CHOICE,
                            twoOptions(),
                            List.of("a"),
                            oneTag(),
                            originality(),
                            early))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("before it was written");
        }

        @Test
        void requiresASourceNoteWithSubstance() {
            assertThatThrownBy(() -> new OriginalityAttestation(
                            "Author One", LocalDate.parse("2026-09-01"), true, "mine"))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    class RequiredText {

        @Test
        @DisplayName("a wrong answer with no explanation teaches nothing")
        void requiresAnExplanation() {
            assertThatThrownBy(() -> new Question(
                            QuestionCode.of("fixture-question"),
                            QuestionType.SINGLE_CHOICE,
                            Difficulty.of(3),
                            STEM,
                            twoOptions(),
                            List.of("a"),
                            "Short.",
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class);
        }

        @Test
        void requiresAStemOfSubstance() {
            assertThatThrownBy(() -> new Question(
                            QuestionCode.of("fixture-question"),
                            QuestionType.SINGLE_CHOICE,
                            Difficulty.of(3),
                            "Too short",
                            twoOptions(),
                            List.of("a"),
                            EXPLANATION,
                            oneTag(),
                            originality(),
                            review()))
                    .isInstanceOf(DomainValidationException.class);
        }
    }

    @Nested
    class Difficulties {

        @ParameterizedTest
        @ValueSource(ints = {0, 6, -1})
        void rejectsDifficultyOutsideOneToFive(int level) {
            assertThatThrownBy(() -> Difficulty.of(level)).isInstanceOf(DomainValidationException.class);
        }

        @Test
        void listsEveryLevelAscending() {
            assertThat(Difficulty.all()).extracting(Difficulty::level).containsExactly(1, 2, 3, 4, 5);
        }
    }

    @Test
    void reportsWhichSubSkillsItCovers() {
        Question question = question("fixture-one", "What is two plus two in this fixture?");

        assertThat(question.covers(SubSkillCode.of("adding-integers"))).isTrue();
        assertThat(question.covers(SubSkillCode.of("adding-fractions"))).isFalse();
    }
}
