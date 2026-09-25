package com.placefy.application.service;

import com.placefy.application.error.InvalidCredentialsException;
import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.DeleteMyAccount;
import com.placefy.application.port.out.PasswordHasher;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.User;
import java.util.Objects;

public class DeleteMyAccountService implements DeleteMyAccount {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;

    public DeleteMyAccountService(UserRepository users, PasswordHasher passwordHasher) {
        this.users = Objects.requireNonNull(users);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    @Override
    public void handle(DeleteMyAccountCommand command) {
        User user = users.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        // Re-authentication, not authorisation. The caller already proved they hold a valid token;
        // this proves they also know the password, which a stolen session does not.
        if (!passwordHasher.matches(command.currentPassword(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        users.deleteById(command.userId());
    }
}
