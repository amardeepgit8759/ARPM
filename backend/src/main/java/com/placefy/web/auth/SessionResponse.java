package com.placefy.web.auth;

import com.placefy.application.port.in.AuthenticatedSession;
import java.time.Instant;

/**
 * What an authentication call returns in its body.
 *
 * <p>The refresh token is deliberately absent: it leaves the server only in an httpOnly cookie,
 * so no script on the page can read it. Adding it here would undo that in one line.
 */
public record SessionResponse(String accessToken, Instant accessTokenExpiresAt, UserResponse user) {

    public static SessionResponse from(AuthenticatedSession session) {
        return new SessionResponse(
                session.accessToken(), session.accessTokenExpiresAt(), UserResponse.from(session.user()));
    }
}
