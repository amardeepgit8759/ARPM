package com.placefy.application.port.out;

import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import java.util.Optional;

/**
 * Storage for taxonomy versions.
 *
 * <p>Split deliberately into {@link #insert} and {@link #markPublished} rather than offering a
 * single {@code save}. The content of a version — its domains, skills, sub-skills and
 * prerequisites — is written once and never touched again; the only thing that ever changes
 * afterwards is the status. A general-purpose {@code save} would make it possible to rewrite
 * published content by accident, which is the one thing this table exists to prevent.
 */
public interface TaxonomyVersionRepository {

    boolean existsByLabel(TaxonomyVersionLabel label);

    Optional<TaxonomyVersion> findByLabel(TaxonomyVersionLabel label);

    /**
     * Writes a complete version and its whole content tree.
     *
     * @throws com.placefy.application.error.TaxonomyLabelAlreadyUsedException if the label is
     *     taken. The use case checks first for a clean error, but only the unique index closes
     *     the race, so adapters must translate that constraint violation here.
     */
    TaxonomyVersion insert(TaxonomyVersion version);

    /** Records the DRAFT to PUBLISHED transition. Touches no content row. */
    TaxonomyVersion markPublished(TaxonomyVersion published);
}
