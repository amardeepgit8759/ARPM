package com.placefy.domain.session;

import java.util.Objects;
import java.util.UUID;

public record RefreshTokenId(UUID value) {

    public RefreshTokenId {
        Objects.requireNonNull(value, "refresh token id must not be null");
    }

    public static RefreshTokenId of(UUID value) {
        return new RefreshTokenId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
