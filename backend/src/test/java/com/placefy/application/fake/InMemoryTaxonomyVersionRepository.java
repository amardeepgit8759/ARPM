package com.placefy.application.fake;

import com.placefy.application.error.TaxonomyLabelAlreadyUsedException;
import com.placefy.application.port.out.TaxonomyVersionRepository;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Honours the same contract as the real adapter, including refusing a duplicate label, so a use
 * case that passes here is not passing for a reason the database would reject.
 */
public class InMemoryTaxonomyVersionRepository implements TaxonomyVersionRepository {

    private final Map<TaxonomyVersionLabel, TaxonomyVersion> byLabel = new LinkedHashMap<>();

    private int insertCount = 0;
    private int publishCount = 0;

    @Override
    public boolean existsByLabel(TaxonomyVersionLabel label) {
        return byLabel.containsKey(label);
    }

    @Override
    public Optional<TaxonomyVersion> findByLabel(TaxonomyVersionLabel label) {
        return Optional.ofNullable(byLabel.get(label));
    }

    @Override
    public TaxonomyVersion insert(TaxonomyVersion version) {
        if (byLabel.containsKey(version.label())) {
            throw new TaxonomyLabelAlreadyUsedException(version.label());
        }
        byLabel.put(version.label(), version);
        insertCount++;
        return version;
    }

    @Override
    public TaxonomyVersion markPublished(TaxonomyVersion published) {
        byLabel.put(published.label(), published);
        publishCount++;
        return published;
    }

    public int insertCount() {
        return insertCount;
    }

    public int publishCount() {
        return publishCount;
    }
}
