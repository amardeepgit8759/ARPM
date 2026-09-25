package com.placefy.infrastructure.config;

import com.placefy.application.port.in.UpdateMyProfile;
import com.placefy.application.port.in.UserProfile;
import org.springframework.transaction.annotation.Transactional;

class TransactionalUpdateMyProfile implements UpdateMyProfile {

    private final UpdateMyProfile delegate;

    TransactionalUpdateMyProfile(UpdateMyProfile delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public UserProfile handle(UpdateMyProfileCommand command) {
        return delegate.handle(command);
    }
}
