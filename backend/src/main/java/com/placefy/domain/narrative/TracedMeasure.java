package com.placefy.domain.narrative;

import com.placefy.domain.DomainValidationException;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One number the scoring engine computed, carried verbatim.
 *
 * <p>The kernel never rounds, rescales or converts this value. It arrives already rounded by
 * whatever rule {@code docs/scoring-spec.md} lays down, and the scale it arrives with is the
 * scale it is rendered with — so a trace saying {@code 62.0} produces the text "62.0", and a
 * narrative that writes "62%" or "about 60" instead is making a claim the engine did not.
 *
 * @param label what the number is, supplied by the producer. Not interpreted here.
 * @param unit rendered immediately after the value; empty for a bare number. A unit is
 *     presentation, not arithmetic: attaching "%" never divides or multiplies anything.
 */
public record TracedMeasure(String label, BigDecimal value, String unit) {

    public TracedMeasure {
        if (label == null || label.isBlank()) {
            throw new DomainValidationException("label", "A traced measure needs a label.");
        }
        Objects.requireNonNull(value, "value");
        unit = unit == null ? "" : unit;
    }

    public static TracedMeasure of(String label, BigDecimal value) {
        return new TracedMeasure(label, value, "");
    }

    public static TracedMeasure of(String label, BigDecimal value, String unit) {
        return new TracedMeasure(label, value, unit);
    }

    /** The exact text a renderer may print for this measure. */
    public String rendered() {
        return value.toPlainString() + unit;
    }
}
