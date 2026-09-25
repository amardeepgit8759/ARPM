package com.placefy.application.port.in;

/**
 * Ends the session a refresh token belongs to.
 *
 * <p>Idempotent and silent by design: logging out with an unknown, expired, already-revoked or
 * missing token succeeds exactly like logging out with a live one. Sign-out is not the place to
 * tell a caller which tokens the server recognises.
 */
public interface Logout {

    void handle(LogoutCommand command);

    /** @param presentedRefreshToken the cookie value, or null when the browser sent none */
    record LogoutCommand(String presentedRefreshToken) {}
}
