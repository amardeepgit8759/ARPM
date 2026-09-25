package com.placefy.infrastructure.config;

import com.placefy.application.port.in.DeleteMyAccount;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletion reads the user, verifies the password and erases the row.
 *
 * <p>One transaction around all three, so a failure partway cannot leave an account half-erased.
 * This is the only irreversible operation in the product; there is no second attempt to get it
 * right.
 */
class TransactionalDeleteMyAccount implements DeleteMyAccount {

    private final DeleteMyAccount delegate;

    TransactionalDeleteMyAccount(DeleteMyAccount delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void handle(DeleteMyAccountCommand command) {
        delegate.handle(command);
    }
}
