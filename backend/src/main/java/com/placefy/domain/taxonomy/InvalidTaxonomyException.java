package com.placefy.domain.taxonomy;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Carries every problem found, not the first.
 *
 * <p>Content authoring is an edit-run-edit loop. Failing on the first bad reference means the
 * author fixes one line, re-runs, and finds the next — so a file with six problems costs six
 * round trips. Collecting them makes the validator a report rather than a tripwire.
 */
public class InvalidTaxonomyException extends RuntimeException {

    private final transient List<TaxonomyViolation> violations;

    public InvalidTaxonomyException(List<TaxonomyViolation> violations) {
        super(format(violations));
        this.violations = List.copyOf(violations);
    }

    public List<TaxonomyViolation> violations() {
        return violations;
    }

    public boolean hasViolationOfType(TaxonomyViolationType type) {
        return violations.stream().anyMatch(violation -> violation.type() == type);
    }

    private static String format(List<TaxonomyViolation> violations) {
        return violations.size()
                + (violations.size() == 1 ? " problem" : " problems")
                + " in taxonomy content:"
                + violations.stream()
                        .map(violation -> System.lineSeparator() + "  - " + violation)
                        .collect(Collectors.joining());
    }
}
