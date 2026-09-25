package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A second person's confirmation that a question is correct and fairly worded.
 *
 * <p>The reviewer is checked against the author elsewhere, in {@link Question}, because
 * self-review is not review — and an unreviewed wrong answer does not merely annoy a student,
 * it moves the sub-skill score that their preparation plan is built from.
 */
public record ReviewerSignOff(String reviewedBy, LocalDate reviewedOn, ReviewOutcome outcome, String comment) {

    public ReviewerSignOff {
        if (reviewedBy == null || reviewedBy.isBlank()) {
            throw new DomainValidationException("reviewedBy", "A sign-off needs a named reviewer.");
        }
        Objects.requireNonNull(reviewedOn, "reviewedOn");
        Objects.requireNonNull(outcome, "outcome");
        reviewedBy = reviewedBy.strip();
        comment = comment == null ? "" : comment.strip();
    }

    public boolean isApproved() {
        return outcome == ReviewOutcome.APPROVED;
    }
}
