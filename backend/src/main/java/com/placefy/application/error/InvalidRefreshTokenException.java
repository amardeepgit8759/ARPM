package com.placefy.application.error;

/**
 * Covers unknown, expired, already-rotated and orphaned tokens alike. Distinguishing them in the
 * response would tell an attacker holding a stolen token whether it was ever valid.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is missing, expired or already used.");
    }
}
