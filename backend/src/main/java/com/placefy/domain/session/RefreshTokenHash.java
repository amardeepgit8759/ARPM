package com.placefy.domain.session;

import com.placefy.domain.DomainValidationException;
import java.util.regex.Pattern;

/**
 * The stored form of a refresh token: a hex digest, never the token itself. A database dump
 * therefore does not hand an attacker usable sessions.
 */
public record RefreshTokenHash(String value) {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

    public RefreshTokenHash {
        if (value == null || !HEX_64.matcher(value).matches()) {
            throw new DomainValidationException(
                    "refreshTokenHash", "Refresh token hash must be 64 lowercase hex characters.");
        }
    }

    public static RefreshTokenHash of(String value) {
        return new RefreshTokenHash(value);
    }

    @Override
    public String toString() {
        return "RefreshTokenHash[" + value.substring(0, 8) + "...]";
    }
}
