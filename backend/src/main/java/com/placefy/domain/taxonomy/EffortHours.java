package com.placefy.domain.taxonomy;

import com.placefy.domain.DomainValidationException;

/**
 * The default study effort attached to a sub-skill, in whole hours.
 *
 * <p>Vocabulary only. This slice attaches no scoring meaning to it whatsoever — it is not a
 * cost, not a weight and not an input to any calculation. What it means for gap cost is a
 * decision for {@code docs/scoring-spec.md}, which is not yet written.
 */
public record EffortHours(int value) {

    public static final int MIN = 1;

    /** Generous by design. A ceiling exists to catch a typo'd 4000, not to express a policy. */
    public static final int MAX = 10_000;

    public EffortHours {
        if (value < MIN) {
            throw new DomainValidationException(
                    "defaultEffortHours", "Default effort hours must be at least " + MIN + ", got " + value + ".");
        }
        if (value > MAX) {
            throw new DomainValidationException(
                    "defaultEffortHours", "Default effort hours must be at most " + MAX + ", got " + value + ".");
        }
    }

    public static EffortHours of(int value) {
        return new EffortHours(value);
    }

    @Override
    public String toString() {
        return value + "h";
    }
}
