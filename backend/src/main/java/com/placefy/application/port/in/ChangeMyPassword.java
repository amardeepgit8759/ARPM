package com.placefy.application.port.in;

import com.placefy.domain.user.UserId;

/**
 * Replaces the caller's password after re-verifying the current one.
 *
 * <p>Returns a fresh session because every existing session is revoked: a password change is
 * what a user does when they suspect someone else is signed in, and leaving that someone's
 * refresh token alive would make the change cosmetic. The device that made the change keeps
 * working through the new session instead.
 */
public interface ChangeMyPassword {

    AuthenticatedSession handle(ChangeMyPasswordCommand command);

    record ChangeMyPasswordCommand(UserId userId, String currentPassword, String newPassword) {

        @Override
        public String toString() {
            return "ChangeMyPasswordCommand[userId=" + userId + ", passwords=REDACTED]";
        }
    }
}
