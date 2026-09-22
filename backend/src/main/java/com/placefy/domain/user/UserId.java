package com.placefy.domain.user;

import com.placefy.domain.DomainValidationException;
import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "user id must not be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    /** Parses an id that arrived from outside the domain, such as a JWT subject. */
    public static UserId parse(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("userId", "User id must be present.");
        }
        try {
            return new UserId(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("userId", "User id is not a valid UUID.");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
