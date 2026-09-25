package com.placefy.domain.narrative;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds every numeric claim in a piece of generated prose.
 *
 * <p>This is the component the whole safety argument rests on. A number it fails to see is a
 * number nothing downstream can check, so it is deliberately eager: anything that reads as a
 * quantity is reported, and the cost of over-reporting is a fallback to the deterministic
 * renderer rather than a wrong figure on a student's screen.
 *
 * <p>Sub-skill names and codes are masked out before extraction. See {@link SubSkillVocabulary}.
 */
final class NumberExtractor {

    /**
     * Grouped thousands first, then decimals, then bare integers — the alternation is ordered so
     * "1,234" is one number rather than "1" and "234". Fractional digits are required after the
     * dot so a sentence-ending "62." yields 62, not a malformed decimal.
     */
    private static final Pattern DIGITS =
            Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");

    private static final Pattern ORDINAL_SUFFIX = Pattern.compile("^(st|nd|rd|th)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");

    private NumberExtractor() {}

    static List<NumericToken> extract(String text, SubSkillVocabulary vocabulary) {
        String masked = vocabulary.maskTerms(text);

        List<NumericToken> tokens = new ArrayList<>(digitTokens(masked));
        tokens.addAll(spelledTokens(masked));
        tokens.sort((a, b) -> Integer.compare(a.startIndex(), b.startIndex()));
        return List.copyOf(tokens);
    }

    private static List<NumericToken> digitTokens(String masked) {
        List<NumericToken> tokens = new ArrayList<>();
        Matcher matcher = DIGITS.matcher(masked);

        while (matcher.find()) {
            String raw = matcher.group();
            int start = matcher.start();
            int end = matcher.end();

            BigDecimal value = new BigDecimal(raw.replace(",", ""));
            NumericTokenKind kind = raw.contains(",")
                    ? NumericTokenKind.GROUPED
                    : raw.contains(".") ? NumericTokenKind.DECIMAL : NumericTokenKind.INTEGER;
            String text = raw;

            String trailing = masked.substring(end);
            if (trailing.startsWith("%")) {
                kind = NumericTokenKind.PERCENTAGE;
                text = raw + "%";
            } else if (trailing.startsWith("x") || trailing.startsWith("X") || trailing.startsWith("×")) {
                // "2x" is a multiplier; "2xyz" is not. Require the x to end the word.
                if (trailing.length() == 1 || !Character.isLetterOrDigit(trailing.charAt(1))) {
                    kind = NumericTokenKind.MULTIPLIER;
                    text = raw + trailing.charAt(0);
                }
            } else {
                Matcher ordinal = ORDINAL_SUFFIX.matcher(trailing);
                if (ordinal.find()) {
                    kind = NumericTokenKind.ORDINAL;
                    text = raw + ordinal.group();
                }
            }

            // A hyphen is a minus sign only when nothing numeric precedes it; between two digits
            // it is a range ("3-5"), and both endpoints are separate claims.
            if (isNegativeSign(masked, start)) {
                value = value.negate();
                kind = NumericTokenKind.NEGATIVE;
                text = "-" + text;
                start = start - 1;
            }

            tokens.add(new NumericToken(text, value, kind, start));
        }
        return tokens;
    }

    /**
     * A hyphen is a minus sign only when it is not joining the number to something. Directly
     * after a digit it separates a range ("3-5"); directly after a letter it is a compound
     * ("top-5"); with whitespace, a bracket or the start of the text before it, it is negation.
     */
    private static boolean isNegativeSign(String masked, int numberStart) {
        int hyphen = numberStart - 1;
        if (hyphen < 0 || masked.charAt(hyphen) != '-') {
            return false;
        }
        int before = hyphen - 1;
        return before < 0 || !Character.isLetterOrDigit(masked.charAt(before));
    }

    private static List<NumericToken> spelledTokens(String masked) {
        List<String> words = new ArrayList<>();
        List<Integer> offsets = new ArrayList<>();

        Matcher matcher = WORD.matcher(masked);
        while (matcher.find()) {
            words.add(matcher.group().toLowerCase(Locale.ROOT));
            offsets.add(matcher.start());
        }

        List<NumericToken> tokens = new ArrayList<>();
        int index = 0;
        while (index < words.size()) {
            Optional<SpelledNumbers.Match> match = SpelledNumbers.matchAt(words, index);
            if (match.isPresent()) {
                SpelledNumbers.Match found = match.get();
                int start = offsets.get(index);
                int lastWord = index + found.wordsConsumed() - 1;
                int end = offsets.get(lastWord) + words.get(lastWord).length();

                tokens.add(new NumericToken(
                        masked.substring(start, end).trim(),
                        found.value(),
                        NumericTokenKind.SPELLED,
                        start));
                index += found.wordsConsumed();
            } else {
                index++;
            }
        }
        return tokens;
    }
}
