package com.placefy.application.port.in;

import com.placefy.domain.taxonomy.TaxonomyVersion;

/**
 * Puts a validated taxonomy version into storage as a DRAFT.
 *
 * <p>Takes an already-built {@link TaxonomyVersion} rather than a file path or raw text.
 * Parsing YAML is an infrastructure concern; by the time a taxonomy reaches this use case it
 * has been validated by the domain, so this layer never handles half-built content.
 */
public interface ImportTaxonomyVersion {

    Result handle(ImportTaxonomyVersionCommand command);

    record ImportTaxonomyVersionCommand(TaxonomyVersion taxonomy) {}

    record Result(TaxonomyVersionSummary summary, Outcome outcome) {}

    enum Outcome {
        /** The version did not exist and was written. */
        IMPORTED,

        /**
         * The label was already present with byte-identical content, so nothing was written.
         * Importing is therefore safe to run on every application start.
         */
        ALREADY_IMPORTED
    }
}
