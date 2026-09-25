package com.placefy.domain.narrative;

import java.util.List;

/**
 * The verdict on one piece of generated prose.
 *
 * <p>Carries every violation rather than the first, so a rejected generation can be diagnosed
 * and the prompt improved from a single log line.
 */
public record NarrativeValidationResult(List<NarrativeViolation> violations) {

    public NarrativeValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static NarrativeValidationResult accepted() {
        return new NarrativeValidationResult(List.of());
    }

    public boolean isAccepted() {
        return violations.isEmpty();
    }

    public boolean isRejected() {
        return !violations.isEmpty();
    }

    public boolean hasViolationOfType(NarrativeViolationType type) {
        return violations.stream().anyMatch(violation -> violation.type() == type);
    }

    public String summary() {
        return violations.isEmpty()
                ? "accepted"
                : violations.size() + " violation(s): " + violations.stream().map(NarrativeViolation::toString).toList();
    }
}
