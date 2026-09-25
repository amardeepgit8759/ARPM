package com.placefy.domain.question;

/** Why a question bank was rejected. */
public enum QuestionViolationType {

    DUPLICATE_QUESTION_CODE,

    /** Two questions whose stems normalise to the same text. */
    DUPLICATE_STEM,

    /** Two stems sharing enough phrasing to be the same question lightly reworded. */
    NEAR_DUPLICATE_STEM,

    /** A tag naming a sub-skill the declared taxonomy version does not contain. */
    UNKNOWN_SUB_SKILL_TAG,

    /** The bank declares a taxonomy version other than the one it was validated against. */
    TAXONOMY_VERSION_MISMATCH,

    /**
     * A field could not be read, or was rejected by a value object. Raised by the loader so one
     * malformed question does not hide problems in the rest of the bank.
     */
    MALFORMED_CONTENT
}
