package com.placefy.domain.taxonomy;

import com.placefy.domain.DomainValidationException;
import java.util.regex.Pattern;

/**
 * Shared validation for the three code types.
 *
 * <p>Codes are lower-case kebab-case slugs. They are the stable identity of a taxonomy node:
 * a persisted scoring run references a sub-skill by code, so a code that changes silently
 * breaks the link between a stored result and the thing it measured. Names may be reworded
 * freely; codes may not.
 */
final class Codes {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final int MAX_LENGTH = 64;

    private Codes() {}

    static String validate(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, field + " is required.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException(
                    field, field + " must be at most " + MAX_LENGTH + " characters: " + value);
        }
        if (!SLUG.matcher(value).matches()) {
            throw new DomainValidationException(
                    field,
                    field + " must be lower-case kebab-case (letters, digits and single hyphens): " + value);
        }
        return value;
    }
}
