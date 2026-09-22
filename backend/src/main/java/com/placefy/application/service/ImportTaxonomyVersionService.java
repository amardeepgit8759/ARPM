package com.placefy.application.service;

import com.placefy.application.error.TaxonomyContentChangedException;
import com.placefy.application.port.in.ImportTaxonomyVersion;
import com.placefy.application.port.in.TaxonomyVersionSummary;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import java.util.Objects;
import java.util.Optional;

public class ImportTaxonomyVersionService implements ImportTaxonomyVersion {

    private final TaxonomyVersionRepository repository;

    public ImportTaxonomyVersionService(TaxonomyVersionRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public Result handle(ImportTaxonomyVersionCommand command) {
        TaxonomyVersion candidate = command.taxonomy();
        Optional<TaxonomyVersion> existing = repository.findByLabel(candidate.label());

        if (existing.isPresent()) {
            TaxonomyVersion stored = existing.get();

            // Same label, same bytes: this is a re-run, not a change. Returning the stored
            // version rather than writing again is what makes seeding safe on every startup.
            if (stored.sourceChecksum().equals(candidate.sourceChecksum())) {
                return new Result(TaxonomyVersionSummary.from(stored), Outcome.ALREADY_IMPORTED);
            }

            // Same label, different bytes. Refusing is the whole point: a version label has to
            // mean one exact vocabulary forever, or no scoring run that cites it is reproducible.
            throw new TaxonomyContentChangedException(
                    candidate.label(), stored.sourceChecksum(), candidate.sourceChecksum());
        }

        TaxonomyVersion imported = repository.insert(candidate);
        return new Result(TaxonomyVersionSummary.from(imported), Outcome.IMPORTED);
    }
}
