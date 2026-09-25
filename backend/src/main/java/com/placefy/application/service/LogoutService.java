package com.placefy.application.service;

import com.placefy.application.port.in.Logout;
import com.placefy.application.port.out.RefreshTokenHasher;
import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.domain.DomainValidationException;
import com.placefy.domain.session.RefreshTokenHash;
import java.util.Objects;
import java.util.Optional;

/**
 * Revokes the whole rotation chain the presented token belongs to, not just the token.
 *
 * <p>The presented token is normally the only live member of its family, but revoking the family
 * also covers a copy that was stolen and rotated elsewhere, which is exactly the session a user
 * signing out most wants dead.
 */
public class LogoutService implements Logout {

    private final RefreshTokenRepository refreshTokens;
    private final RefreshTokenHasher tokenHasher;
    private final TimeProvider time;

    public LogoutService(RefreshTokenRepository refreshTokens, RefreshTokenHasher tokenHasher, TimeProvider time) {
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.time = Objects.requireNonNull(time);
    }

    @Override
    public void handle(LogoutCommand command) {
        hashOf(command.presentedRefreshToken())
                .flatMap(refreshTokens::findByHash)
                .ifPresent(token -> refreshTokens.revokeFamily(token.familyId(), time.now()));
    }

    private Optional<RefreshTokenHash> hashOf(String presented) {
        if (presented == null || presented.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(tokenHasher.hash(presented));
        } catch (DomainValidationException e) {
            return Optional.empty();
        }
    }
}
