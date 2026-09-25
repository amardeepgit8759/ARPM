package com.placefy.domain.question;

/** What a reviewer decided. Only {@link #APPROVED} lets a question into the bank. */
public enum ReviewOutcome {
    APPROVED,
    CHANGES_REQUESTED,
    REJECTED
}
