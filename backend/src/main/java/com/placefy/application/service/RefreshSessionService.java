package com.placefy.application.service;

import com.placefy.application.error.InvalidRefreshTokenException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.RefreshSession;
import com.placefy.application.port.out.RefreshTokenHasher;
import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.DomainValidationException;
import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenHash;
import com.placefy.domain.user.User;
import java.time.Instant;
import java.util.Objects;

/**
 * Exchanges a refresh token for a new session, rotating the token and detecting replay.
 *
 * <p>Every rejection path throws the same exception with the same message. A caller cannot tell
 * an unknown token from an expired one from a replayed one.
 */
public class RefreshSessionService implements RefreshSession {

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final RefreshTokenHasher tokenHasher;
    private final SessionIssuer sessions;
    private final TimeProvider time;

    public RefreshSessionService(
            RefreshTokenRepository refreshTokens,
            UserRepository users,
            RefreshTokenHasher tokenHasher,
            SessionIssuer sessions,
            TimeProvider time) {
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.users = Objects.requireNonNull(users);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.sessions = Objects.requireNonNull(sessions);
        this.time = Objects.requireNonNull(time);
    }

    @Override
    public AuthenticatedSession handle(RefreshSessionCommand command) {
        RefreshToken stored = findStoredToken(command.presentedRefreshToken());
        Instant now = time.now();

        if (stored.isRevoked()) {
            // The client already traded this token in. Either it replayed, or someone else holds
            // a copy. We cannot tell which, so we assume theft and kill the whole chain: the
            // legitimate user is logged out and re-authenticates, the thief gets nothing.
            refreshTokens.revokeFamily(stored.familyId(), now);
            throw new InvalidRefreshTokenException();
        }

        if (stored.isExpiredAt(now)) {
            throw new InvalidRefreshTokenException();
        }

        User user = users.findById(stored.userId()).orElseThrow(InvalidRefreshTokenException::new);

        // Retire the presented token before minting its replacement, so a token is never usable
        // twice even if issuing the new session fails.
        refreshTokens.save(stored.revokeAt(now));

        return sessions.issueFor(user, stored.familyId(), now);
    }

    private RefreshToken findStoredToken(String presented) {
        RefreshTokenHash hash;
        try {
            hash = tokenHasher.hash(presented);
        } catch (DomainValidationException | NullPointerException e) {
            throw new InvalidRefreshTokenException();
        }
        return refreshTokens.findByHash(hash).orElseThrow(InvalidRefreshTokenException::new);
    }
}
