package com.placefy.domain.taxonomy;

/**
 * Why a taxonomy was rejected.
 *
 * <p>A type rather than a message so tests can assert on the reason, and so the content
 * validator can group failures, without either depending on prose that may be reworded.
 */
public enum TaxonomyViolationType {

    /** The taxonomy declares no domains. */
    EMPTY_TAXONOMY,

    /** A domain declares no skills. */
    EMPTY_DOMAIN,

    /** A skill declares no sub-skills. */
    EMPTY_SKILL,

    DUPLICATE_DOMAIN_CODE,

    DUPLICATE_SKILL_CODE,

    DUPLICATE_SUB_SKILL_CODE,

    /** A prerequisite names a sub-skill code that does not exist anywhere in the taxonomy. */
    UNKNOWN_PREREQUISITE,

    /** A sub-skill lists itself as its own prerequisite. */
    SELF_PREREQUISITE,

    /** The same prerequisite is listed twice for one sub-skill. */
    DUPLICATE_PREREQUISITE,

    /** The prerequisite graph contains a cycle, so no study order could satisfy it. */
    PREREQUISITE_CYCLE,

    /**
     * A field could not be read at all — absent, wrong type, or rejected by a value object.
     * Raised by the content loader rather than by the aggregate, so that one malformed field
     * does not hide structural problems elsewhere in the same file.
     */
    MALFORMED_CONTENT
}
