package com.placefy.application.service;

import com.placefy.application.error.InvalidCredentialsException;
import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.ChangeMyPassword;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.PasswordHasher;
import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.user.RawPassword;
import com.placefy.domain.user.User;
import java.time.Instant;
import java.util.Objects;

public class ChangeMyPasswordService implements ChangeMyPassword {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordHasher passwordHasher;
    private final SessionIssuer sessions;
    private final TimeProvider time;
    private final IdGenerator ids;

    public ChangeMyPasswordService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordHasher passwordHasher,
            SessionIssuer sessions,
            TimeProvider time,
            IdGenerator ids) {
        this.users = Objects.requireNonNull(users);
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
        this.sessions = Objects.requireNonNull(sessions);
        this.time = Objects.requireNonNull(time);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public AuthenticatedSession handle(ChangeMyPasswordCommand command) {
        User user = users.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        // Re-authentication first: a stolen access token alone must not be able to lock the
        // owner out by setting a password only the thief knows.
        if (!passwordHasher.matches(command.currentPassword(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        // The new password meets today's policy even if the old one predates it.
        RawPassword newPassword = RawPassword.of(command.newPassword());
        Instant now = time.now();

        User changed = users.save(user.changePassword(passwordHasher.hash(newPassword), now));
        refreshTokens.revokeAllForUser(user.id(), now);

        return sessions.issueFor(changed, RefreshTokenFamilyId.of(ids.newId()), now);
    }
}
