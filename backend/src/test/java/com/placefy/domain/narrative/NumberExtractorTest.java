package com.placefy.domain.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A number this misses is a number nothing downstream can check, so these cases are the floor of
 * the safety argument rather than incidental parsing tests.
 */
class NumberExtractorTest {

    private final SubSkillVocabulary vocabulary = TraceFixtures.vocabulary();

    private List<BigDecimal> valuesIn(String text) {
        return NumberExtractor.extract(text, vocabulary).stream()
                .map(NumericToken::value)
                .toList();
    }

    @Nested
    class Formats {

        static Stream<Arguments> numericForms() {
            return Stream.of(
                    Arguments.of("Your score is 62.", List.of("62")),
                    Arguments.of("Your score is 62.5 today.", List.of("62.5")),
                    Arguments.of("You matched 48% of the bar.", List.of("48")),
                    Arguments.of("A total of 1,234 candidates.", List.of("1234")),
                    Arguments.of("Around 1,234.5 hours.", List.of("1234.5")),
                    Arguments.of("Spend 3-5 hours a week.", List.of("3", "5")),
                    Arguments.of("Your 1st gap matters most.", List.of("1")),
                    Arguments.of("The 22nd item.", List.of("22")),
                    Arguments.of("The 3rd and 4th items.", List.of("3", "4")),
                    Arguments.of("That is 2x the target.", List.of("2")),
                    Arguments.of("That is 1.5x the target.", List.of("1.5")),
                    Arguments.of("A change of -5 points.", List.of("-5")),
                    Arguments.of("Scores of 62 and 48 and 31.", List.of("62", "48", "31")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("numericForms")
        void readsEveryNumericForm(String text, List<String> expected) {
            assertThat(valuesIn(text))
                    .containsExactlyElementsOf(expected.stream().map(BigDecimal::new).toList());
        }

        @Test
        @DisplayName("a trailing sentence period is not a decimal point")
        void sentenceEndIsNotADecimal() {
            assertThat(valuesIn("Your score is 62. That is below target."))
                    .containsExactly(new BigDecimal("62"));
        }

        @Test
        @DisplayName("a hyphen between digits is a range, not a minus sign")
        void rangeIsNotNegation() {
            assertThat(valuesIn("Spend 3-5 hours."))
                    .containsExactly(new BigDecimal("3"), new BigDecimal("5"));
        }

        @Test
        @DisplayName("a hyphen joining a word is a compound, not negation")
        void hyphenatedCompoundIsNotNegative() {
            assertThat(valuesIn("Your top-3 gaps.")).containsExactly(new BigDecimal("3"));
        }

        @Test
        @DisplayName("2xyz is not a multiplier")
        void multiplierNeedsAWordBoundary() {
            assertThat(valuesIn("Variable 2xyz here.")).containsExactly(new BigDecimal("2"));
        }
    }

    @Nested
    class SpelledOut {

        static Stream<Arguments> spelledForms() {
            return Stream.of(
                    Arguments.of("Your readiness is sixty-two.", List.of("62")),
                    Arguments.of("Your readiness is sixty two.", List.of("62")),
                    Arguments.of("You have three gaps.", List.of("3")),
                    Arguments.of("One hundred twenty three points.", List.of("123")),
                    Arguments.of("Two thousand hours.", List.of("2000")),
                    Arguments.of("The first gap.", List.of("1")),
                    Arguments.of("The twentieth item.", List.of("20")),
                    Arguments.of("Less than half the target.", List.of("0.5")),
                    Arguments.of("About a quarter of the way.", List.of("0.25")),
                    Arguments.of("Ninety percent complete.", List.of("90")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("spelledForms")
        void readsSpelledNumbers(String text, List<String> expected) {
            assertThat(valuesIn(text))
                    .containsExactlyElementsOf(expected.stream().map(BigDecimal::new).toList());
        }

        @Test
        @DisplayName("adjacent unrelated units are two numbers, not a compound")
        void doesNotMergeAdjacentUnits() {
            assertThat(valuesIn("one one")).containsExactly(new BigDecimal("1"), new BigDecimal("1"));
        }

        @Test
        @DisplayName("a spelled number stated instead of a digit is still caught")
        void spellingOutDoesNotEvadeDetection() {
            assertThat(valuesIn("Your readiness sits at sixty-two out of one hundred."))
                    .containsExactly(new BigDecimal("62"), new BigDecimal("100"));
        }
    }

    @Nested
    class IdentifierMasking {

        @Test
        @DisplayName("digits inside a sub-skill code are not a claim")
        void digitsInCodesAreIgnored() {
            assertThat(valuesIn("Focus on dp-on-grids next.")).isEmpty();
        }

        @Test
        @DisplayName("a spelled number inside a sub-skill NAME is not a claim")
        void spelledNumbersInsideNamesAreIgnored() {
            // The shipped taxonomy really does contain "Two-Pointer Technique". Extracting before
            // masking would make every correct mention of it assert the number two.
            assertThat(valuesIn("Two-Pointer Technique needs work.")).isEmpty();
        }

        @Test
        @DisplayName("masking a name does not hide a real number beside it")
        void maskingDoesNotSwallowNeighbouringNumbers() {
            assertThat(valuesIn("Two-Pointer Technique scored 58."))
                    .containsExactly(new BigDecimal("58"));
        }

        @Test
        @DisplayName("a term only matches whole, so 'arrays' inside another word is untouched")
        void masksWholeTermsOnly() {
            assertThat(valuesIn("Array Traversal covers 12 patterns."))
                    .containsExactly(new BigDecimal("12"));
        }

        @Test
        void maskingPreservesOffsetsSoReportsPointAtTheRightPlace() {
            List<NumericToken> tokens =
                    NumberExtractor.extract("Two-Pointer Technique scored 58.", vocabulary);

            assertThat(tokens).hasSize(1);
            assertThat(tokens.get(0).startIndex()).isEqualTo("Two-Pointer Technique scored ".length());
        }
    }

    @Nested
    class NothingToFind {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Your preparation is progressing well.",
                    "Focus on graph traversal before moving on.",
                    "",
                    "   "
                })
        void findsNoNumbersWhereThereAreNone(String text) {
            assertThat(valuesIn(text)).isEmpty();
        }
    }
}
