package com.placefy.infrastructure.config;

import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.RegisterUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts a transaction around registration, which writes a user and a refresh token.
 *
 * <p>The boundary lives here rather than on the use case because {@code @Transactional} is a
 * Spring annotation and the application layer has none. A decorator per write use case is the
 * price of that rule; it is a small, explicit price and it keeps the transaction boundary
 * somewhere a reader can actually see it.
 */
class TransactionalRegisterUser implements RegisterUser {

    private final RegisterUser delegate;

    TransactionalRegisterUser(RegisterUser delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public AuthenticatedSession handle(RegisterUserCommand command) {
        return delegate.handle(command);
    }
}
