package com.placefy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.session.RefreshTokenHash;
import com.placefy.domain.session.RefreshTokenId;
import com.placefy.domain.user.Email;
import com.placefy.domain.user.FullName;
import com.placefy.domain.user.PasswordHash;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import com.placefy.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RefreshTokenRepositoryAdapterIT extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofDays(30));

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    private UserId userId;

    @BeforeEach
    void clearTablesAndSeedAUser() {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");
        userId = UserId.of(UUID.randomUUID());
        users.save(User.register(
                userId,
                FullName.of("Ada Lovelace"),
                Email.of("ada@example.com"),
                PasswordHash.of("$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012345"),
                NOW));
    }

    @Test
    void savesAndReadsBackATokenUnchanged() {
        RefreshToken saved = refreshTokens.save(token(hashOf("a"), RefreshTokenFamilyId.of(UUID.randomUUID())));

        RefreshToken loaded = refreshTokens.findByHash(saved.tokenHash()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.userId()).isEqualTo(userId);
        assertThat(loaded.familyId()).isEqualTo(saved.familyId());
        assertThat(loaded.issuedAt()).isEqualTo(NOW);
        assertThat(loaded.expiresAt()).isEqualTo(EXPIRES);
        assertThat(loaded.isRevoked()).isFalse();
    }

    @Test
    void persistsRevocation() {
        RefreshToken saved = refreshTokens.save(token(hashOf("b"), RefreshTokenFamilyId.of(UUID.randomUUID())));

        refreshTokens.save(saved.revokeAt(NOW.plusSeconds(60)));

        RefreshToken loaded = refreshTokens.findByHash(saved.tokenHash()).orElseThrow();
        assertThat(loaded.isRevoked()).isTrue();
        assertThat(loaded.revokedAt()).contains(NOW.plusSeconds(60));
    }

    @Test
    void reportsAbsenceForAnUnknownHash() {
        assertThat(refreshTokens.findByHash(hashOf("never-stored"))).isEmpty();
    }

    @Test
    @DisplayName("revoking a family kills every live token in it and leaves other families alone")
    void revokesOneFamilyOnly() {
        RefreshTokenFamilyId compromised = RefreshTokenFamilyId.of(UUID.randomUUID());
        RefreshTokenFamilyId untouched = RefreshTokenFamilyId.of(UUID.randomUUID());
        refreshTokens.save(token(hashOf("c1"), compromised));
        refreshTokens.save(token(hashOf("c2"), compromised));
        refreshTokens.save(token(hashOf("u1"), untouched));

        int revoked = refreshTokens.revokeFamily(compromised, NOW.plusSeconds(10));

        assertThat(revoked).isEqualTo(2);
        assertThat(refreshTokens.findByHash(hashOf("c1")).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokens.findByHash(hashOf("c2")).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokens.findByHash(hashOf("u1")).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("re-revoking a family changes nothing and preserves the original instants")
    void familyRevocationIsIdempotent() {
        RefreshTokenFamilyId family = RefreshTokenFamilyId.of(UUID.randomUUID());
        refreshTokens.save(token(hashOf("d"), family));

        assertThat(refreshTokens.revokeFamily(family, NOW.plusSeconds(10))).isEqualTo(1);
        assertThat(refreshTokens.revokeFamily(family, NOW.plusSeconds(99))).isZero();

        assertThat(refreshTokens.findByHash(hashOf("d")).orElseThrow().revokedAt())
                .contains(NOW.plusSeconds(10));
    }

    @Test
    @DisplayName("deleting a user takes their sessions with them")
    void tokensCascadeWithTheirUser() {
        refreshTokens.save(token(hashOf("e"), RefreshTokenFamilyId.of(UUID.randomUUID())));

        jdbc.update("DELETE FROM users WHERE id = ?", userId.value());

        assertThat(refreshTokens.findByHash(hashOf("e"))).isEmpty();
    }

    private RefreshToken token(RefreshTokenHash hash, RefreshTokenFamilyId familyId) {
        return RefreshToken.issue(
                RefreshTokenId.of(UUID.randomUUID()), userId, familyId, hash, NOW, EXPIRES);
    }

    /** Distinct 64-hex digests without needing a real hasher in the test. */
    private static RefreshTokenHash hashOf(String seed) {
        String hex = Integer.toHexString(seed.hashCode());
        return RefreshTokenHash.of(("0".repeat(64 - hex.length()) + hex).toLowerCase());
    }
}
