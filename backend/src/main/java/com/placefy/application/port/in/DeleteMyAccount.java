package com.placefy.application.port.in;

import com.placefy.domain.user.UserId;

/**
 * Erases an account and everything belonging to it.
 *
 * <p>The current password is required even though the caller already holds a valid access token.
 * A token can be stolen; re-entering a password is the one check a thief with a hijacked session
 * cannot pass, and this is the single irreversible operation in the product.
 */
public interface DeleteMyAccount {

    void handle(DeleteMyAccountCommand command);

    record DeleteMyAccountCommand(UserId userId, String currentPassword) {}
}
