package com.placefy.application.service;

import com.placefy.application.error.InvalidCredentialsException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.Login;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.PasswordHasher;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.DomainValidationException;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.user.Email;
import com.placefy.domain.user.User;
import java.util.Objects;
import java.util.Optional;

public class LoginService implements Login {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessions;
    private final TimeProvider time;
    private final IdGenerator ids;

    public LoginService(
            UserRepository users,
            PasswordHasher passwordHasher,
            SessionIssuer sessions,
            TimeProvider time,
            IdGenerator ids) {
        this.users = Objects.requireNonNull(users);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.sessions = Objects.requireNonNull(sessions);
        this.time = Objects.requireNonNull(time);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public AuthenticatedSession handle(LoginCommand command) {
        Optional<User> found = lookup(command.email());

        if (found.isEmpty()) {
            // Verify against a decoy so an unknown address costs the same as a known one.
            // Dropping this makes response time an account-enumeration oracle.
            passwordHasher.matches(command.password(), passwordHasher.decoyHash());
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        if (!passwordHasher.matches(command.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        return sessions.issueFor(user, RefreshTokenFamilyId.of(ids.newId()), time.now());
    }

    /**
     * A malformed address is treated as "no such user", not as a validation error. Login is not
     * the place to teach an address format, and a 400 here would distinguish "badly shaped" from
     * "not registered" — a distinction worth nothing to a user and something to an attacker.
     */
    private Optional<User> lookup(String rawEmail) {
        try {
            return users.findByEmail(Email.of(rawEmail));
        } catch (DomainValidationException e) {
            return Optional.empty();
        }
    }
}
