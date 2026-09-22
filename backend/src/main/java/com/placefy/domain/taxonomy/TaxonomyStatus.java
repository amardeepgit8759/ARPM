package com.placefy.domain.taxonomy;

public enum TaxonomyStatus {

    /** Imported and validated, but not yet usable by anything that scores or is read publicly. */
    DRAFT,

    /**
     * In circulation. A published version is frozen: scoring runs reference it by label, so
     * changing its content afterwards would silently rewrite the meaning of stored results.
     */
    PUBLISHED
}
