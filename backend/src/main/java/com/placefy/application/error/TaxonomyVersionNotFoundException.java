package com.placefy.application.error;

import com.placefy.domain.taxonomy.TaxonomyVersionLabel;

public class TaxonomyVersionNotFoundException extends RuntimeException {

    private final transient TaxonomyVersionLabel label;

    public TaxonomyVersionNotFoundException(TaxonomyVersionLabel label) {
        super("No taxonomy version labelled '" + label + "'.");
        this.label = label;
    }

    public TaxonomyVersionLabel label() {
        return label;
    }
}
