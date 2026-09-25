package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import java.util.List;
import java.util.stream.IntStream;

/**
 * How hard a question is, on the 1–5 scale the product uses.
 *
 * <p>A label, not a measurement. What difficulty does to a score is a decision for
 * {@code docs/scoring-spec.md} §2, which is unwritten — so nothing here weights, scales or
 * compares difficulties beyond ordering them.
 */
public record Difficulty(int level) implements Comparable<Difficulty> {

    public static final int EASIEST = 1;
    public static final int HARDEST = 5;

    public Difficulty {
        if (level < EASIEST || level > HARDEST) {
            throw new DomainValidationException(
                    "difficulty",
                    "Difficulty must be between " + EASIEST + " and " + HARDEST + ", got " + level + ".");
        }
    }

    public static Difficulty of(int level) {
        return new Difficulty(level);
    }

    /** Every level, ascending. Used by the coverage report so empty levels still show up. */
    public static List<Difficulty> all() {
        return IntStream.rangeClosed(EASIEST, HARDEST).mapToObj(Difficulty::of).toList();
    }

    @Override
    public int compareTo(Difficulty other) {
        return Integer.compare(this.level, other.level);
    }

    @Override
    public String toString() {
        return String.valueOf(level);
    }
}
