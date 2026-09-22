package com.placefy.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant ISSUED = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant EXPIRES = ISSUED.plusSeconds(3600);
    private static final RefreshTokenHash HASH = RefreshTokenHash.of("a".repeat(64));

    @Test
    void aFreshTokenIsUsable() {
        assertThat(issue().isUsableAt(ISSUED)).isTrue();
    }

    @Test
    @DisplayName("expiry is inclusive: the token is dead at exactly expiresAt")
    void expiryBoundaryIsInclusive() {
        RefreshToken token = issue();
        assertThat(token.isExpiredAt(EXPIRES.minusMillis(1))).isFalse();
        assertThat(token.isExpiredAt(EXPIRES)).isTrue();
        assertThat(token.isExpiredAt(EXPIRES.plusMillis(1))).isTrue();
    }

    @Test
    void revokingDoesNotMutateTheOriginal() {
        RefreshToken token = issue();
        RefreshToken revoked = token.revokeAt(ISSUED.plusSeconds(10));

        assertThat(token.isRevoked()).isFalse();
        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.isUsableAt(ISSUED.plusSeconds(11))).isFalse();
        assertThat(revoked.revokedAt()).contains(ISSUED.plusSeconds(10));
    }

    @Test
    @DisplayName("re-revoking keeps the first revocation instant")
    void revocationIsIdempotent() {
        RefreshToken revoked = issue().revokeAt(ISSUED.plusSeconds(10));
        assertThat(revoked.revokeAt(ISSUED.plusSeconds(99)).revokedAt()).contains(ISSUED.plusSeconds(10));
    }

    @Test
    void rejectsATokenThatExpiresBeforeItIsIssued() {
        assertThatThrownBy(() -> RefreshToken.issue(
                        RefreshTokenId.of(UUID.randomUUID()),
                        UserId.of(UUID.randomUUID()),
                        RefreshTokenFamilyId.of(UUID.randomUUID()),
                        HASH,
                        ISSUED,
                        ISSUED))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void toStringDoesNotLeakTheHash() {
        assertThat(issue().toString()).doesNotContain(HASH.value());
    }

    private RefreshToken issue() {
        return RefreshToken.issue(
                RefreshTokenId.of(UUID.randomUUID()),
                UserId.of(UUID.randomUUID()),
                RefreshTokenFamilyId.of(UUID.randomUUID()),
                HASH,
                ISSUED,
                EXPIRES);
    }
}
