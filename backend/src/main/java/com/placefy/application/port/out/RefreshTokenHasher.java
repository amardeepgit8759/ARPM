package com.placefy.application.port.out;

import com.placefy.domain.session.RefreshTokenHash;

/**
 * Turns a presented refresh token into its stored form. Unlike passwords this is a fast digest,
 * not a work-factor hash: the token is 256 bits of entropy from a CSPRNG, so there is nothing to
 * brute-force, and refresh happens on a hot path.
 */
public interface RefreshTokenHasher {

    RefreshTokenHash hash(String rawToken);
}
