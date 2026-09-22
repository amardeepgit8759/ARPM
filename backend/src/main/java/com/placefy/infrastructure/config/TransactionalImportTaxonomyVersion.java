package com.placefy.infrastructure.config;

import com.placefy.application.port.in.ImportTaxonomyVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps the check-then-insert so a second importer cannot slip between them, and so a taxonomy
 * tree is written whole or not at all. A half-written vocabulary would validate on load and then
 * be missing sub-skills that assessment items are tagged with.
 */
class TransactionalImportTaxonomyVersion implements ImportTaxonomyVersion {

    private final ImportTaxonomyVersion delegate;

    TransactionalImportTaxonomyVersion(ImportTaxonomyVersion delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Result handle(ImportTaxonomyVersionCommand command) {
        return delegate.handle(command);
    }
}
