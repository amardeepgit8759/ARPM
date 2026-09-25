package com.placefy.domain.question;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Carries every problem found, not the first.
 *
 * <p>Authoring questions is an edit-run-edit loop. A bank with six problems should cost one run
 * to diagnose rather than six.
 */
public class InvalidQuestionBankException extends RuntimeException {

    private final transient List<QuestionViolation> violations;

    public InvalidQuestionBankException(List<QuestionViolation> violations) {
        super(format(violations));
        this.violations = List.copyOf(violations);
    }

    public List<QuestionViolation> violations() {
        return violations;
    }

    public boolean hasViolationOfType(QuestionViolationType type) {
        return violations.stream().anyMatch(violation -> violation.type() == type);
    }

    private static String format(List<QuestionViolation> violations) {
        return violations.size()
                + (violations.size() == 1 ? " problem" : " problems")
                + " in the question bank:"
                + violations.stream()
                        .map(violation -> System.lineSeparator() + "  - " + violation)
                        .collect(Collectors.joining());
    }
}
