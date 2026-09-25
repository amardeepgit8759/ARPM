package com.placefy.infrastructure.config;

import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.ChangeMyPassword;
import org.springframework.transaction.annotation.Transactional;

/**
 * Three writes that must land together: the new hash, the revocation of every old session, and
 * the new session. A new password with old sessions still alive defeats the point of changing
 * it; revoked sessions with no replacement logs the user out of the device they just used.
 */
class TransactionalChangeMyPassword implements ChangeMyPassword {

    private final ChangeMyPassword delegate;

    TransactionalChangeMyPassword(ChangeMyPassword delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public AuthenticatedSession handle(ChangeMyPasswordCommand command) {
        return delegate.handle(command);
    }
}
