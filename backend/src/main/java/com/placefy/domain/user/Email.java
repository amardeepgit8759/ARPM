package com.placefy.domain.user;

import com.placefy.domain.DomainValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A normalised email address. Normalisation is lower-casing and trimming, which is what
 * makes the plain unique index on {@code users.email} a correct uniqueness rule: two
 * registrations differing only in case collide in the database rather than creating
 * two accounts that look identical to a human.
 */
public record Email(String value) {

    /** Deliberately permissive: one @, no whitespace, at least one dot in the domain. */
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    private static final int MAX_LENGTH = 254;

    public Email {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("email", "Email is required.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("email", "Email must be at most " + MAX_LENGTH + " characters.");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new DomainValidationException("email", "Email is not a valid address.");
        }
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new DomainValidationException("email", "Email must be normalised before construction.");
        }
    }

    public static Email of(String raw) {
        if (raw == null) {
            throw new DomainValidationException("email", "Email is required.");
        }
        return new Email(raw.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
