package com.placefy.domain.taxonomy;

import com.placefy.domain.DomainValidationException;

/** The human-readable label of a domain, skill or sub-skill. Free to reword; codes are not. */
public record NodeName(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 160;

    public NodeName {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("name", "Name is required.");
        }
        value = value.trim().replaceAll("\\s+", " ");
        if (value.length() < MIN_LENGTH) {
            throw new DomainValidationException("name", "Name must be at least " + MIN_LENGTH + " characters.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException("name", "Name must be at most " + MAX_LENGTH + " characters.");
        }
    }

    public static NodeName of(String value) {
        return new NodeName(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
