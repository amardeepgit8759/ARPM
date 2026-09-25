package com.placefy.domain.narrative;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * The closed set of numbers a narrative may state.
 *
 * <p>Membership is by numeric value, not by text. 62, 62.0 and 62.00 are the same claim, and a
 * narrative writing any of them when the engine computed 62 is telling the truth. There is no
 * tolerance band: "about 60" against a computed 62 is a different number and is rejected, because
 * a student reading 60 has been told something false, however gently.
 *
 * <p>Held in a {@link TreeSet}, whose membership test uses {@code compareTo} rather than
 * {@code equals} — {@code BigDecimal.equals} compares scale, so a {@code HashSet} would say 62.0
 * is absent from a set containing 62 and reject honest prose.
 */
public final class AllowedNumbers {

    private final TreeSet<BigDecimal> values;

    private AllowedNumbers(Collection<BigDecimal> values) {
        this.values = new TreeSet<>(values);
    }

    public static AllowedNumbers of(Collection<BigDecimal> values) {
        Objects.requireNonNull(values, "values");
        values.forEach(value -> Objects.requireNonNull(value, "an allowed number must not be null"));
        return new AllowedNumbers(values);
    }

    public static AllowedNumbers of(BigDecimal... values) {
        return of(List.of(values));
    }

    public boolean permits(BigDecimal candidate) {
        return values.contains(candidate);
    }

    /** Sorted and de-duplicated by value, so a trace serialises identically every time. */
    public List<BigDecimal> values() {
        return List.copyOf(values);
    }

    public int size() {
        return values.size();
    }

    /**
     * Value equality, by the same {@code compareTo} rule as membership — so a set holding 62 and
     * one holding 62.0 are the same set. Without this, {@link DecisionTrace} would inherit
     * identity equality through its record component and two structurally identical traces would
     * compare unequal, which quietly breaks any caching or de-duplication keyed on a trace.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof AllowedNumbers that && this.values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
