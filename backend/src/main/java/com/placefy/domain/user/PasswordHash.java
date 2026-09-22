package com.placefy.domain.user;

import com.placefy.domain.DomainValidationException;

/**
 * The stored result of hashing a password. The domain treats it as an opaque string: it
 * knows a hash exists and can be persisted, but nothing about the algorithm that produced
 * it. Verification belongs to the {@code PasswordHasher} port.
 */
public final class PasswordHash {

    private static final int MAX_LENGTH = 100;

    private final String value;

    private PasswordHash(String value) {
        this.value = value;
    }

    public static PasswordHash of(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("passwordHash", "Password hash is required.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException(
                    "passwordHash", "Password hash must be at most " + MAX_LENGTH + " characters.");
        }
        return new PasswordHash(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PasswordHash that && this.value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "PasswordHash[REDACTED]";
    }
}
