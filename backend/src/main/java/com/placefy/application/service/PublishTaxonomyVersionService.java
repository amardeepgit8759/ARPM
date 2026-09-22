package com.placefy.application.service;

import com.placefy.application.error.TaxonomyVersionNotFoundException;
import com.placefy.application.port.in.PublishTaxonomyVersion;
import com.placefy.application.port.in.TaxonomyVersionSummary;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import java.util.Objects;

public class PublishTaxonomyVersionService implements PublishTaxonomyVersion {

    private final TaxonomyVersionRepository repository;
    private final TimeProvider time;

    public PublishTaxonomyVersionService(TaxonomyVersionRepository repository, TimeProvider time) {
        this.repository = Objects.requireNonNull(repository);
        this.time = Objects.requireNonNull(time);
    }

    @Override
    public TaxonomyVersionSummary handle(PublishTaxonomyVersionCommand command) {
        TaxonomyVersion stored = repository
                .findByLabel(command.label())
                .orElseThrow(() -> new TaxonomyVersionNotFoundException(command.label()));

        // The aggregate decides whether this transition is legal and refuses a second publish;
        // this layer only supplies the instant and persists the outcome.
        TaxonomyVersion published = stored.publish(time.now());

        return TaxonomyVersionSummary.from(repository.markPublished(published));
    }
}
