package com.placefy.application.error;

import com.placefy.domain.taxonomy.ContentChecksum;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;

/**
 * Raised when a label that is already imported is re-imported from different content.
 *
 * <p>This is the mistake version labels cannot catch on their own: editing a taxonomy file
 * without bumping the version. Left unchecked, two scoring runs would both claim to have used
 * "2026.1" while having been computed against different vocabularies, and neither would be
 * reproducible. The fix is always the same — bump the label in the manifest.
 */
public class TaxonomyContentChangedException extends RuntimeException {

    private final transient TaxonomyVersionLabel label;

    public TaxonomyContentChangedException(
            TaxonomyVersionLabel label, ContentChecksum stored, ContentChecksum presented) {
        super("Taxonomy version '" + label + "' is already imported from different content (stored " + stored
                + ", presented " + presented + "). A published version is immutable: bump the "
                + "taxonomy_version in the manifest instead of editing content in place.");
        this.label = label;
    }

    public TaxonomyVersionLabel label() {
        return label;
    }
}
