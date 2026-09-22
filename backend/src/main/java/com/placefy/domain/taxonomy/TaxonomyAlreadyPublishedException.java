package com.placefy.domain.taxonomy;

/**
 * Raised when something tries to publish a version that is already in circulation.
 *
 * <p>A published version is referenced by stored results. Publishing it again would move its
 * {@code publishedAt}, which is a quiet rewrite of a record declared immutable.
 */
public class TaxonomyAlreadyPublishedException extends RuntimeException {

    private final transient TaxonomyVersionLabel label;

    public TaxonomyAlreadyPublishedException(TaxonomyVersionLabel label) {
        super("Taxonomy version '" + label + "' is already published.");
        this.label = label;
    }

    public TaxonomyVersionLabel label() {
        return label;
    }
}
