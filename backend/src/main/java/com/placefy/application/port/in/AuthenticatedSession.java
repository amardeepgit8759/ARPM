package com.placefy.application.port.in;

import java.time.Instant;

/**
 * The result of any successful authentication.
 *
 * <p>{@code refreshToken} is the only time the raw secret exists outside the client: it is
 * generated, hashed for storage, and returned here once. The web layer puts it straight into an
 * httpOnly cookie and it is never readable again.
 */
public record AuthenticatedSession(
        UserProfile user,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt) {}
