package com.placefy.domain.session;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies one unbroken chain of rotated refresh tokens, from the login that started it to
 * the token currently held by the client. Reuse detection works at family granularity: if any
 * token in a family is presented twice, the whole family is revoked, because the second
 * presenter is either the legitimate client or a thief and we cannot tell which.
 */
public record RefreshTokenFamilyId(UUID value) {

    public RefreshTokenFamilyId {
        Objects.requireNonNull(value, "refresh token family id must not be null");
    }

    public static RefreshTokenFamilyId of(UUID value) {
        return new RefreshTokenFamilyId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
