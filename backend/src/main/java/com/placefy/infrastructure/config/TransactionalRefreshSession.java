package com.placefy.infrastructure.config;

import com.placefy.application.error.InvalidRefreshTokenException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.RefreshSession;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotation retires one token and issues another. Without a single transaction around both, a
 * failure between them would leave the client holding a token the server has already revoked and
 * no replacement — a silent logout with no way to diagnose it.
 */
class TransactionalRefreshSession implements RefreshSession {

    private final RefreshSession delegate;

    TransactionalRefreshSession(RefreshSession delegate) {
        this.delegate = delegate;
    }

    /**
     * {@code noRollbackFor} is load-bearing, not a convenience.
     *
     * <p>Replay detection revokes the compromised family and then rejects the request. Under a
     * plain {@code @Transactional} the exception rolls that revocation back, so an attacker
     * replaying a token would be told "no" while every token in the family stayed alive — the
     * defence would appear to work and do nothing. The only write on any rejection path is that
     * revocation, and it is precisely the write that must survive.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthenticatedSession handle(RefreshSessionCommand command) {
        return delegate.handle(command);
    }
}
