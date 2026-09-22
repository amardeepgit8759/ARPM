package com.placefy.infrastructure.config;

import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.Login;
import org.springframework.transaction.annotation.Transactional;

class TransactionalLogin implements Login {

    private final Login delegate;

    TransactionalLogin(Login delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public AuthenticatedSession handle(LoginCommand command) {
        return delegate.handle(command);
    }
}
