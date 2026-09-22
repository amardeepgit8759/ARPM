package com.placefy.application.error;

import com.placefy.domain.user.UserId;

/**
 * Raised when a token authenticates a user that no longer exists — a valid signature over a
 * deleted account. Not reachable through any user-supplied identifier.
 */
public class UserNotFoundException extends RuntimeException {

    private final UserId userId;

    public UserNotFoundException(UserId userId) {
        super("No user with id " + userId);
        this.userId = userId;
    }

    public UserId userId() {
        return userId;
    }
}
