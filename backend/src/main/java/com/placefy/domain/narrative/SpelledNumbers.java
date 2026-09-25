package com.placefy.domain.narrative;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads numbers written as words.
 *
 * <p>Without this, "your readiness is sixty-two" walks straight past a validator that only looks
 * for digits — and spelling a number out is exactly what a language model does when asked to
 * write naturally.
 *
 * <p>Coverage is bounded on purpose: cardinals to 999,999, ordinals to one hundredth, and the
 * two fractions that appear in this kind of prose. Anything outside that range is not silently
 * accepted — it simply is not recognised as a number, and the digits-based extractor is what
 * catches the cases that matter. Where recognition is ambiguous the kernel errs towards reading
 * a word as a number, because a false rejection costs prose quality while a false acceptance
 * puts a wrong figure in front of a student.
 */
final class SpelledNumbers {

    /** A recognised number and how many words it spanned. */
    record Match(BigDecimal value, int wordsConsumed) {}

    private static final Map<String, Integer> UNITS = Map.ofEntries(
            Map.entry("zero", 0),
            Map.entry("one", 1),
            Map.entry("two", 2),
            Map.entry("three", 3),
            Map.entry("four", 4),
            Map.entry("five", 5),
            Map.entry("six", 6),
            Map.entry("seven", 7),
            Map.entry("eight", 8),
            Map.entry("nine", 9),
            Map.entry("ten", 10),
            Map.entry("eleven", 11),
            Map.entry("twelve", 12),
            Map.entry("thirteen", 13),
            Map.entry("fourteen", 14),
            Map.entry("fifteen", 15),
            Map.entry("sixteen", 16),
            Map.entry("seventeen", 17),
            Map.entry("eighteen", 18),
            Map.entry("nineteen", 19));

    private static final Map<String, Integer> TENS = Map.of(
            "twenty", 20,
            "thirty", 30,
            "forty", 40,
            "fifty", 50,
            "sixty", 60,
            "seventy", 70,
            "eighty", 80,
            "ninety", 90);

    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            Map.entry("first", 1),
            Map.entry("second", 2),
            Map.entry("third", 3),
            Map.entry("fourth", 4),
            Map.entry("fifth", 5),
            Map.entry("sixth", 6),
            Map.entry("seventh", 7),
            Map.entry("eighth", 8),
            Map.entry("ninth", 9),
            Map.entry("tenth", 10),
            Map.entry("eleventh", 11),
            Map.entry("twelfth", 12),
            Map.entry("thirteenth", 13),
            Map.entry("fourteenth", 14),
            Map.entry("fifteenth", 15),
            Map.entry("sixteenth", 16),
            Map.entry("seventeenth", 17),
            Map.entry("eighteenth", 18),
            Map.entry("nineteenth", 19),
            Map.entry("twentieth", 20),
            Map.entry("thirtieth", 30),
            Map.entry("fortieth", 40),
            Map.entry("fiftieth", 50),
            Map.entry("sixtieth", 60),
            Map.entry("seventieth", 70),
            Map.entry("eightieth", 80),
            Map.entry("ninetieth", 90),
            Map.entry("hundredth", 100));

    private static final Map<String, BigDecimal> FRACTIONS =
            Map.of("half", new BigDecimal("0.5"), "quarter", new BigDecimal("0.25"));

    private SpelledNumbers() {}

    /**
     * Attempts to read a number beginning at {@code index}.
     *
     * @return the value and the number of words it spanned, or empty if no number starts here
     */
    static Optional<Match> matchAt(List<String> words, int index) {
        String first = words.get(index);

        if (ORDINALS.containsKey(first)) {
            return Optional.of(new Match(BigDecimal.valueOf(ORDINALS.get(first)), 1));
        }
        if (FRACTIONS.containsKey(first)) {
            return Optional.of(new Match(FRACTIONS.get(first), 1));
        }

        long total = 0;
        long current = 0;
        int consumed = 0;
        boolean lastWasUnit = false;
        boolean lastWasTens = false;

        while (index + consumed < words.size()) {
            String word = words.get(index + consumed);

            if (UNITS.containsKey(word)) {
                // "one one" is two separate numbers, not eleven. Stop rather than accumulate.
                if (lastWasUnit) {
                    break;
                }
                current += UNITS.get(word);
                lastWasUnit = true;
                lastWasTens = false;
            } else if (TENS.containsKey(word)) {
                if (lastWasTens || lastWasUnit) {
                    break;
                }
                current += TENS.get(word);
                lastWasTens = true;
            } else if ("hundred".equals(word)) {
                current = current == 0 ? 100 : current * 100;
                lastWasUnit = false;
                lastWasTens = false;
            } else if ("thousand".equals(word)) {
                total += (current == 0 ? 1 : current) * 1000;
                current = 0;
                lastWasUnit = false;
                lastWasTens = false;
            } else {
                break;
            }

            consumed++;
        }

        if (consumed == 0) {
            return Optional.empty();
        }
        return Optional.of(new Match(BigDecimal.valueOf(total + current), consumed));
    }
}
