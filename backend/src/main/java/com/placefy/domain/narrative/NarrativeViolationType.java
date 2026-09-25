package com.placefy.domain.narrative;

/** Why generated prose was refused. */
public enum NarrativeViolationType {

    /** A number appears that the scoring run did not produce. The reason this kernel exists. */
    UNLISTED_NUMBER,

    /** A sub-skill is named that this run's trace does not cover. */
    SUB_SKILL_NOT_IN_TRACE,

    MISSING_SECTION,

    UNEXPECTED_SECTION,

    TOO_SHORT,

    TOO_LONG,

    /** Nothing at all was generated. */
    EMPTY
}
