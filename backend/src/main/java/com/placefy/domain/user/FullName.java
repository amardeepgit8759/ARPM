package com.placefy.domain.user;

import com.placefy.domain.DomainValidationException;

public record FullName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 120;

    public FullName {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("name", "Name is required.");
        }
        if (value.length() < MIN_LENGTH) {
            throw new DomainValidationException("name", "Name must be at least " + MIN_LENGTH + " characters.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("name", "Name must be at most " + MAX_LENGTH + " characters.");
        }
    }

    /** Trims and collapses internal whitespace so "Ada   Lovelace" and "Ada Lovelace" are one name. */
    public static FullName of(String raw) {
        if (raw == null) {
            throw new DomainValidationException("name", "Name is required.");
        }
        return new FullName(raw.trim().replaceAll("\\s+", " "));
    }

    @Override
    public String toString() {
        return value;
    }
}
