package com.placefy.application.service;

import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.UserProfile;
import com.placefy.application.port.out.AccessTokenIssuer;
import com.placefy.application.port.out.IdGenerator;
import com.placefy.application.port.out.RefreshTokenGenerator;
import com.placefy.application.port.out.RefreshTokenHasher;
import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.session.RefreshTokenId;
import com.placefy.domain.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Mints one access token and one refresh token for a user and records the refresh token.
 *
 * <p>Shared by register, login and refresh so that all three produce sessions with identical
 * lifetimes and storage. Not a port — it is application logic with no outside implementation.
 */
public class SessionIssuer {

    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenGenerator tokenGenerator;
    private final RefreshTokenHasher tokenHasher;
    private final IdGenerator ids;
    private final Duration refreshTokenTtl;

    public SessionIssuer(
            RefreshTokenRepository refreshTokens,
            AccessTokenIssuer accessTokens,
            RefreshTokenGenerator tokenGenerator,
            RefreshTokenHasher tokenHasher,
            IdGenerator ids,
            Duration refreshTokenTtl) {
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.accessTokens = Objects.requireNonNull(accessTokens);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
        this.ids = Objects.requireNonNull(ids);
        this.refreshTokenTtl = Objects.requireNonNull(refreshTokenTtl);
    }

    /**
     * @param familyId a new family for a login or registration, or the existing family when
     *     rotating, so that one stolen token can revoke exactly the chain it belongs to
     */
    public AuthenticatedSession issueFor(User user, RefreshTokenFamilyId familyId, Instant now) {
        AccessTokenIssuer.IssuedAccessToken accessToken = accessTokens.issue(user, now);

        String rawRefreshToken = tokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.issue(
                RefreshTokenId.of(ids.newId()),
                user.id(),
                familyId,
                tokenHasher.hash(rawRefreshToken),
                now,
                now.plus(refreshTokenTtl));
        refreshTokens.save(refreshToken);

        return new AuthenticatedSession(
                UserProfile.from(user),
                accessToken.value(),
                accessToken.expiresAt(),
                rawRefreshToken,
                refreshToken.expiresAt());
    }
}
