package com.placefy.application.service;

import com.placefy.application.error.EmailAlreadyRegisteredException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.RegisterUser;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.PasswordHasher;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.user.Email;
import com.placefy.domain.user.FullName;
import com.placefy.domain.user.RawPassword;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;

public class RegisterUserService implements RegisterUser {

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessions;
    private final TimeProvider time;
    private final IdGenerator ids;

    public RegisterUserService(
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
    public AuthenticatedSession handle(RegisterUserCommand command) {
        FullName name = FullName.of(command.name());
        Email email = Email.of(command.email());
        RawPassword password = RawPassword.of(command.password());

        // Checked here so the common case gets a clean 409 rather than a constraint violation.
        // The unique index is still the authority under concurrency; the adapter maps it to the
        // same exception, so both paths look identical to the caller.
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        Instant now = time.now();
        User registered = users.save(User.register(
                UserId.of(ids.newId()), name, email, passwordHasher.hash(password), now));

        return sessions.issueFor(registered, RefreshTokenFamilyId.of(ids.newId()), now);
    }
}
