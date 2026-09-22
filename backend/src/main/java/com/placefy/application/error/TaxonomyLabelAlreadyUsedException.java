package com.placefy.application.error;

import com.placefy.domain.taxonomy.TaxonomyVersionLabel;

public class TaxonomyLabelAlreadyUsedException extends RuntimeException {

    private final transient TaxonomyVersionLabel label;

    public TaxonomyLabelAlreadyUsedException(TaxonomyVersionLabel label) {
        super("Taxonomy version '" + label + "' already exists.");
        this.label = label;
    }

    public TaxonomyVersionLabel label() {
        return label;
    }
}
