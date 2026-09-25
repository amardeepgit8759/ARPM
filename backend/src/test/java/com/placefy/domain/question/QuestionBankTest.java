package com.placefy.domain.question;

import static com.placefy.domain.question.QuestionFixtures.NEAR_DUPLICATE_THRESHOLD;
import static com.placefy.domain.question.QuestionFixtures.TAXONOMY_VERSION;
import static com.placefy.domain.question.QuestionFixtures.bank;
import static com.placefy.domain.question.QuestionFixtures.question;
import static com.placefy.domain.question.QuestionFixtures.taxonomy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QuestionBankTest {

    @Nested
    class Uniqueness {

        @Test
        void acceptsDistinctQuestions() {
            assertThatCode(() -> bank(
                            question("one", "What is two plus two in this fixture?"),
                            question("two", "How many sides does a hexagon have here?")))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsARepeatedQuestionCode() {
            assertThatThrownBy(() -> bank(
                            question("same-code", "What is two plus two in this fixture?"),
                            question("same-code", "How many sides does a hexagon have here?")))
                    .isInstanceOf(InvalidQuestionBankException.class)
                    .satisfies(hasType(QuestionViolationType.DUPLICATE_QUESTION_CODE));
        }
    }

    @Nested
    class Duplicates {

        @Test
        @DisplayName("the same stem typed differently is still the same question")
        void catchesIdenticalStemsAcrossFormatting() {
            assertThatThrownBy(() -> bank(
                            question("one", "What is two plus two in this fixture?"),
                            question("two", "WHAT IS   two plus  two, in this fixture?!")))
                    .isInstanceOf(InvalidQuestionBankException.class)
                    .satisfies(hasType(QuestionViolationType.DUPLICATE_STEM));
        }

        @Test
        @DisplayName("a lightly reworded copy is caught by shared phrasing")
        void catchesNearDuplicates() {
            assertThatThrownBy(() -> bank(
                            question("one", "Which data structure gives constant time lookup on average?"),
                            question("two", "Which data structure gives constant time lookup on average here?")))
                    .isInstanceOf(InvalidQuestionBankException.class)
                    .satisfies(hasType(QuestionViolationType.NEAR_DUPLICATE_STEM));
        }

        @Test
        @DisplayName("two questions on the same topic are not duplicates just for sharing vocabulary")
        void doesNotFlagDifferentQuestionsOnOneTopic() {
            assertThatCode(() -> bank(
                            question("one", "Which data structure gives constant time lookup on average?"),
                            question(
                                    "two",
                                    "When a hash table resizes, what happens to the cost of a single insert?")))
                    .doesNotThrowAnyException();
        }

        @Test
        void namesBothQuestionsInTheReport() {
            assertThatThrownBy(() -> bank(
                            question("original-question", "What is two plus two in this fixture?"),
                            question("copied-question", "What is two plus two in this fixture?")))
                    .hasMessageContaining("original-question")
                    .hasMessageContaining("copied-question");
        }
    }

    @Nested
    class TaxonomyAgreement {

        @Test
        void rejectsATagTheTaxonomyDoesNotContain() {
            assertThatThrownBy(() -> bank(question(
                            "one", "What is two plus two in this fixture?", 3, "not-in-the-taxonomy")))
                    .isInstanceOf(InvalidQuestionBankException.class)
                    .satisfies(hasType(QuestionViolationType.UNKNOWN_SUB_SKILL_TAG));
        }

        @Test
        @DisplayName("a bank written for a different taxonomy version is refused outright")
        void rejectsATaxonomyVersionMismatch() {
            assertThatThrownBy(() -> QuestionBank.of(
                            "test-bank-1",
                            "some-other-version",
                            List.of(question("one", "What is two plus two in this fixture?")),
                            taxonomy(),
                            NEAR_DUPLICATE_THRESHOLD))
                    .isInstanceOf(InvalidQuestionBankException.class)
                    .satisfies(hasType(QuestionViolationType.TAXONOMY_VERSION_MISMATCH));
        }
    }

    @Nested
    class Reporting {

        @Test
        @DisplayName("every problem is reported at once, not one per run")
        void reportsEveryProblemInOnePass() {
            assertThatThrownBy(() -> bank(
                            question("dup", "What is two plus two in this fixture?"),
                            question("dup", "What is two plus two in this fixture?"),
                            question("bad-tag", "How many sides does a hexagon have here?", 2, "no-such-skill")))
                    .isInstanceOf(InvalidQuestionBankException.class)
                    .satisfies(e -> assertThat(((InvalidQuestionBankException) e).violations())
                            .extracting(QuestionViolation::type)
                            .contains(
                                    QuestionViolationType.DUPLICATE_QUESTION_CODE,
                                    QuestionViolationType.UNKNOWN_SUB_SKILL_TAG));
        }
    }

    @Nested
    class Queries {

        @Test
        void anEmptyBankIsValid() {
            QuestionBank empty =
                    QuestionBank.of("empty", TAXONOMY_VERSION, List.of(), taxonomy(), NEAR_DUPLICATE_THRESHOLD);

            assertThat(empty.isEmpty()).isTrue();
            assertThat(empty.size()).isZero();
        }

        @Test
        void findsQuestionsByTagAndDifficulty() {
            QuestionBank bank = bank(
                    question("one", "What is two plus two in this fixture?", 2, "adding-integers"),
                    question("two", "How many sides does a hexagon have here?", 4, "adding-fractions"));

            assertThat(bank.taggedWith(SubSkillCode.of("adding-integers")))
                    .extracting(q -> q.code().value())
                    .containsExactly("one");
            assertThat(bank.atDifficulty(Difficulty.of(4)))
                    .extracting(q -> q.code().value())
                    .containsExactly("two");
        }
    }

    private static java.util.function.Consumer<Throwable> hasType(QuestionViolationType type) {
        return e -> assertThat(((InvalidQuestionBankException) e).hasViolationOfType(type))
                .as("expected a %s violation, got %s", type, ((InvalidQuestionBankException) e).violations())
                .isTrue();
    }
}
