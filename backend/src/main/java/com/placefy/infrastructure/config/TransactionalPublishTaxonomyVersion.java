package com.placefy.infrastructure.config;

import com.placefy.application.port.in.PublishTaxonomyVersion;
import com.placefy.application.port.in.TaxonomyVersionSummary;
import org.springframework.transaction.annotation.Transactional;

class TransactionalPublishTaxonomyVersion implements PublishTaxonomyVersion {

    private final PublishTaxonomyVersion delegate;

    TransactionalPublishTaxonomyVersion(PublishTaxonomyVersion delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public TaxonomyVersionSummary handle(PublishTaxonomyVersionCommand command) {
        return delegate.handle(command);
    }
}
