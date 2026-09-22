package com.placefy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.InvalidRefreshTokenException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.RefreshSession.RefreshSessionCommand;
import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.session.RefreshTokenHash;
import com.placefy.domain.session.RefreshTokenId;
import com.placefy.domain.user.UserId;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshSessionServiceTest extends AuthUseCaseFixture {

    @Test
    void exchangesAValidTokenForANewSession() {
        AuthenticatedSession registered = registerAda();

        AuthenticatedSession refreshed = refreshSession.handle(new RefreshSessionCommand(registered.refreshToken()));

        assertThat(refreshed.user()).isEqualTo(registered.user());
        assertThat(refreshed.refreshToken()).isNotEqualTo(registered.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("the presented token is retired and its replacement stays in the same family")
    void rotatesWithinTheSameFamily() {
        AuthenticatedSession registered = registerAda();
        RefreshToken original = refreshTokens.all().get(0);

        refreshSession.handle(new RefreshSessionCommand(registered.refreshToken()));

        RefreshToken retired = refreshTokens.findByHash(original.tokenHash()).orElseThrow();
        assertThat(retired.isRevoked()).isTrue();
        assertThat(refreshTokens.liveTokens()).hasSize(1);
        assertThat(refreshTokens.liveTokens().get(0).familyId()).isEqualTo(original.familyId());
    }

    @Test
    void theOldTokenStopsWorkingOnceRotated() {
        AuthenticatedSession registered = registerAda();
        refreshSession.handle(new RefreshSessionCommand(registered.refreshToken()));

        assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(registered.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("replaying a rotated token revokes the entire chain, not just that token")
    void replayRevokesTheWholeFamily() {
        AuthenticatedSession registered = registerAda();
        refreshSession.handle(new RefreshSessionCommand(registered.refreshToken()));
        assertThat(refreshTokens.liveTokens()).hasSize(1);

        assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(registered.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokens.liveTokens())
                .as("the thief and the legitimate client are both logged out")
                .isEmpty();
    }

    @Test
    @DisplayName("a family revoked by replay does not affect a separate login")
    void replayDoesNotRevokeUnrelatedSessions() {
        AuthenticatedSession first = registerAda();
        AuthenticatedSession second =
                login.handle(new com.placefy.application.port.in.Login.LoginCommand(
                        "ada@example.com", "correct-horse-battery"));

        refreshSession.handle(new RefreshSessionCommand(first.refreshToken()));
        assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(first.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshSession.handle(new RefreshSessionCommand(second.refreshToken())).user())
                .isEqualTo(second.user());
    }

    @Test
    void rejectsAnExpiredToken() {
        AuthenticatedSession registered = registerAda();
        time.advanceBy(REFRESH_TTL.plus(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(registered.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("a token is dead at exactly its expiry instant")
    void expiryBoundaryIsInclusive() {
        AuthenticatedSession registered = registerAda();
        time.advanceBy(REFRESH_TTL);

        assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(registered.refreshToken())))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-real-token", "refresh-token-999"})
    void rejectsUnknownOrMalformedTokens(String presented) {
        registerAda();

        assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(presented)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("a token whose user no longer exists is rejected, not resolved to somebody else")
    void rejectsATokenForAMissingUser() {
        String orphanSecret = "orphaned-token";
        refreshTokens.save(RefreshToken.issue(
                RefreshTokenId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                RefreshTokenFamilyId.of(UUID.randomUUID()),
                tokenHasher.hash(orphanSecret),
                NOW,
                NOW.plus(REFRESH_TTL)));

        assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(orphanSecret)))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("a refresh token only ever resolves to the user it was issued to")
    void aTokenNeverCrossesToAnotherUser() {
        AuthenticatedSession ada = registerAda();
        AuthenticatedSession grace = register("Grace Hopper", "grace@example.com", "another-good-password");

        assertThat(refreshSession.handle(new RefreshSessionCommand(ada.refreshToken())).user())
                .isEqualTo(ada.user());
        assertThat(refreshSession.handle(new RefreshSessionCommand(grace.refreshToken())).user())
                .isEqualTo(grace.user());
    }

    @Test
    @DisplayName("hash lookups are exact: a near-miss does not resolve to a stored token")
    void lookupIsByExactHash() {
        registerAda();
        RefreshTokenHash unrelated = tokenHasher.hash("some-other-secret");

        assertThat(refreshTokens.findByHash(unrelated)).isEmpty();
    }
}
