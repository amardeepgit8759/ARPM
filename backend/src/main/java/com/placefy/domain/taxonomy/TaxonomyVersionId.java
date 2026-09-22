package com.placefy.domain.taxonomy;

import java.util.Objects;
import java.util.UUID;

public record TaxonomyVersionId(UUID value) {

    public TaxonomyVersionId {
        Objects.requireNonNull(value, "taxonomy version id must not be null");
    }

    public static TaxonomyVersionId of(UUID value) {
        return new TaxonomyVersionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
