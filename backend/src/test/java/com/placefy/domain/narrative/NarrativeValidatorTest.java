package com.placefy.domain.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NarrativeValidatorTest {

    private final DecisionTrace trace = TraceFixtures.trace();
    private final NarrativeValidator validator = new NarrativeValidator(TraceFixtures.vocabulary());
    private final NarrativeFormat format = DecisionTraceRenderer.format();

    private NarrativeValidationResult validate(String narrative) {
        return validator.validate(narrative, trace, format);
    }

    /** A skeleton with all four required headings, so a test can vary one thing at a time. */
    private static String withBody(String standingBody) {
        return """
                ## Where you stand
                %s

                ## Your biggest gaps
                Graph Traversal (BFS and DFS) is the one to fix first.

                ## What is working
                Array Traversal is in good shape.

                ## Consistency
                Your evidence is reasonably consistent.
                """
                .formatted(standingBody);
    }

    @Nested
    class Numbers {

        @Test
        void acceptsNumbersTheRunProduced() {
            assertThat(validate(withBody("Your readiness is 62 and you match 48% of the bar."))
                            .isAccepted())
                    .isTrue();
        }

        @Test
        @DisplayName("a number the run never produced is refused")
        void rejectsAnInventedNumber() {
            NarrativeValidationResult result = validate(withBody("Your readiness is 71."));

            assertThat(result.hasViolationOfType(NarrativeViolationType.UNLISTED_NUMBER)).isTrue();
            assertThat(result.violations().get(0).offendingText()).isEqualTo("71");
        }

        @Test
        @DisplayName("a plausible near-miss is still a different number")
        void rejectsRoundingTheRealValue() {
            // 59 appears nowhere in the trace. "About 59" reads gently and is false.
            assertThat(validate(withBody("Your readiness is about 59.")).isRejected()).isTrue();
        }

        @Test
        @DisplayName("KNOWN LIMIT: a real number used for the wrong quantity is not caught")
        void doesNotDetectAPermittedNumberUsedInTheWrongPlace() {
            // 70 is a genuine value in this trace - the graph-traversal threshold - so stating it
            // as the overall readiness passes. The validator checks which numbers may appear, not
            // which fact each one belongs to, and closing that gap needs the trace to say where
            // each figure is allowed to be used rather than merely that it exists.
            //
            // Pinned as a test so the limit is a recorded decision rather than an assumption
            // somebody later mistakes for a guarantee. The mitigation today is that the
            // deterministic renderer, not the model, is what labels each figure.
            assertThat(validate(withBody("Your readiness is 70.")).isAccepted()).isTrue();
        }

        @Test
        @DisplayName("62.0 and 62 are the same claim")
        void acceptsTheSameValueAtADifferentScale() {
            assertThat(validate(withBody("Your readiness is 62.0.")).isAccepted()).isTrue();
        }

        @Test
        @DisplayName("converting 62 to 0.62 is arithmetic the engine did not do")
        void rejectsUnitConversion() {
            assertThat(validate(withBody("Your readiness is 0.62.")).isRejected()).isTrue();
        }

        @Test
        @DisplayName("spelling an invented number out does not get past the check")
        void rejectsSpelledInventedNumbers() {
            assertThat(validate(withBody("Your readiness is seventy-one.")).isRejected())
                    .isTrue();
        }

        @Test
        @DisplayName("a sum the model worked out itself is refused, even though both inputs are allowed")
        void rejectsDerivedArithmetic() {
            // 70 - 31 = 39. Both 70 and 31 are in the trace; 39 is not, because no scoring rule
            // produced it. Deriving it is precisely the behaviour this kernel exists to stop.
            assertThat(validate(withBody("You are 39 points short.")).isRejected()).isTrue();
        }

        @Test
        @DisplayName("counts and positions describing the trace itself are permitted")
        void acceptsStructuralNumbers() {
            assertThat(validate(withBody("You have 3 gaps, and the 1st is the most urgent."))
                            .isAccepted())
                    .isTrue();
        }

        @Test
        @DisplayName("a count larger than the trace holds is not structural and is refused")
        void rejectsCountsBeyondTheTrace() {
            assertThat(validate(withBody("You have 9 gaps.")).isRejected()).isTrue();
        }

        @Test
        void reportsEveryBadNumberNotJustTheFirst() {
            NarrativeValidationResult result = validate(withBody("Scores of 71, 72 and 73."));

            assertThat(result.violations()).hasSize(3);
        }
    }

    @Nested
    class SubSkills {

        @Test
        void acceptsSubSkillsThisRunMeasured() {
            assertThat(validate(withBody("Graph Traversal (BFS and DFS) needs work.")).isAccepted())
                    .isTrue();
        }

        @Test
        @DisplayName("a real sub-skill this run never looked at is refused")
        void rejectsASubSkillOutsideTheTrace() {
            // Memoisation exists in the taxonomy but is not among this run's gaps or strengths.
            // This is the subtler failure: it reads as authoritative and is simply not about
            // anything that was measured.
            NarrativeValidationResult result = validate(withBody("Memoisation is holding you back."));

            assertThat(result.hasViolationOfType(NarrativeViolationType.SUB_SKILL_NOT_IN_TRACE))
                    .isTrue();
        }

        @Test
        void refersToSubSkillsByCodeAsWellAsName() {
            assertThat(validate(withBody("Work on os-deadlock-conditions.")).isRejected()).isTrue();
        }
    }

    @Nested
    class Shape {

        @Test
        void rejectsAMissingSection() {
            String missingConsistency =
                    """
                    ## Where you stand
                    Your readiness is 62.

                    ## Your biggest gaps
                    Graph Traversal (BFS and DFS).

                    ## What is working
                    Array Traversal.
                    """;

            assertThat(validator.validate(missingConsistency, trace, format)
                            .hasViolationOfType(NarrativeViolationType.MISSING_SECTION))
                    .isTrue();
        }

        @Test
        @DisplayName("an invented section is refused, because it is usually advice nobody computed")
        void rejectsAnUnexpectedSection() {
            NarrativeValidationResult result = validate(
                    withBody("Your readiness is 62.") + System.lineSeparator() + "## Next steps"
                            + System.lineSeparator() + "Study harder.");

            assertThat(result.hasViolationOfType(NarrativeViolationType.UNEXPECTED_SECTION))
                    .isTrue();
        }

        @Test
        void rejectsTextThatIsTooShort() {
            NarrativeFormat strict = NarrativeFormat.of(List.of(), 500, 900);

            assertThat(validator.validate("Too brief.", trace, strict)
                            .hasViolationOfType(NarrativeViolationType.TOO_SHORT))
                    .isTrue();
        }

        @Test
        void rejectsTextThatIsTooLong() {
            NarrativeFormat strict = NarrativeFormat.of(List.of(), 1, 20);

            assertThat(validator.validate("This sentence is comfortably longer than twenty characters.", trace, strict)
                            .hasViolationOfType(NarrativeViolationType.TOO_LONG))
                    .isTrue();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\n\n"})
        void rejectsNothingAtAll(String narrative) {
            assertThat(validator.validate(narrative, trace, format)
                            .hasViolationOfType(NarrativeViolationType.EMPTY))
                    .isTrue();
        }
    }

    @Nested
    class Determinism {

        @Test
        void thesameTextAndTraceAlwaysGiveTheSameVerdict() {
            String narrative = withBody("Your readiness is 71 and Memoisation is weak.");

            NarrativeValidationResult first = validate(narrative);
            NarrativeValidationResult second = validate(narrative);

            assertThat(second.violations()).isEqualTo(first.violations());
        }
    }
}
