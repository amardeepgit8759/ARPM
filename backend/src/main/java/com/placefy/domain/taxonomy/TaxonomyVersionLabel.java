package com.placefy.domain.taxonomy;

import com.placefy.domain.DomainValidationException;
import java.util.regex.Pattern;

/**
 * The human-chosen identity of a taxonomy version, declared in the content manifest.
 *
 * <p>Author-supplied rather than auto-incremented because this is the value a scoring run
 * records as {@code taxonomy_version}, and it has to mean one exact set of content forever.
 * Tying it to the reviewed content rather than to an insertion counter keeps that promise
 * legible to the person editing the YAML.
 */
public record TaxonomyVersionLabel(String value) {

    private static final Pattern SHAPE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    public TaxonomyVersionLabel {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("taxonomyVersion", "Taxonomy version label is required.");
        }
        value = value.trim();
        if (!SHAPE.matcher(value).matches()) {
            throw new DomainValidationException(
                    "taxonomyVersion",
                    "Taxonomy version label must be 1-64 characters of letters, digits, dot, underscore or "
                            + "hyphen, starting with a letter or digit: " + value);
        }
    }

    public static TaxonomyVersionLabel of(String value) {
        return new TaxonomyVersionLabel(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
