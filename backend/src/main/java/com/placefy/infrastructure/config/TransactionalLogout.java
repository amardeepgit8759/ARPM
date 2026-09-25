package com.placefy.infrastructure.config;

import com.placefy.application.port.in.Logout;
import org.springframework.transaction.annotation.Transactional;

/** The lookup and the family revocation must see the same rows, or a racing refresh slips between. */
class TransactionalLogout implements Logout {

    private final Logout delegate;

    TransactionalLogout(Logout delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void handle(LogoutCommand command) {
        delegate.handle(command);
    }
}
