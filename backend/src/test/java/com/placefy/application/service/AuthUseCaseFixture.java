package com.placefy.application.service;

import com.placefy.application.fake.FakeAccessTokenIssuer;
import com.placefy.application.fake.FakePasswordHasher;
import com.placefy.application.fake.FakeRefreshTokenHasher;
import com.placefy.application.fake.FixedTimeProvider;
import com.placefy.application.fake.InMemoryRefreshTokenRepository;
import com.placefy.application.fake.InMemoryUserRepository;
import com.placefy.application.fake.SequentialIdGenerator;
import com.placefy.application.fake.SequentialRefreshTokenGenerator;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.GetCurrentUser;
import com.placefy.application.port.in.Login;
import com.placefy.application.port.in.RefreshSession;
import com.placefy.application.port.in.RegisterUser;
import java.time.Duration;
import java.time.Instant;

/**
 * Wires the four use cases onto fake ports. Every collaborator is deterministic, so any test
 * extending this can assert on exact ids, exact tokens and exact instants.
 */
abstract class AuthUseCaseFixture {

    static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    static final Duration REFRESH_TTL = Duration.ofDays(30);

    final InMemoryUserRepository users = new InMemoryUserRepository();
    final InMemoryRefreshTokenRepository refreshTokens = new InMemoryRefreshTokenRepository();
    final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    final SequentialRefreshTokenGenerator tokenGenerator = new SequentialRefreshTokenGenerator();
    final FakeRefreshTokenHasher tokenHasher = new FakeRefreshTokenHasher();
    final FixedTimeProvider time = new FixedTimeProvider(NOW);
    final SequentialIdGenerator ids = new SequentialIdGenerator();

    final SessionIssuer sessions = new SessionIssuer(
            refreshTokens, new FakeAccessTokenIssuer(), tokenGenerator, tokenHasher, ids, REFRESH_TTL);

    final RegisterUser registerUser = new RegisterUserService(users, passwordHasher, sessions, time, ids);
    final Login login = new LoginService(users, passwordHasher, sessions, time, ids);
    final RefreshSession refreshSession =
            new RefreshSessionService(refreshTokens, users, tokenHasher, sessions, time);
    final GetCurrentUser getCurrentUser = new GetCurrentUserService(users);

    AuthenticatedSession register(String name, String email, String password) {
        return registerUser.handle(new RegisterUser.RegisterUserCommand(name, email, password));
    }

    AuthenticatedSession registerAda() {
        return register("Ada Lovelace", "ada@example.com", "correct-horse-battery");
    }
}
