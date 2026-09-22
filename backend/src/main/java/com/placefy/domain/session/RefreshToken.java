package com.placefy.domain.session;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One refresh token in a rotation chain.
 *
 * <p>Immutable and clock-free: every decision about validity takes the current instant as an
 * argument, so "is this token expired" is a pure function of the token and a timestamp.
 */
public final class RefreshToken {

    private final RefreshTokenId id;
    private final UserId userId;
    private final RefreshTokenFamilyId familyId;
    private final RefreshTokenHash tokenHash;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant revokedAt;

    private RefreshToken(
            RefreshTokenId id,
            UserId userId,
            RefreshTokenFamilyId familyId,
            RefreshTokenHash tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.familyId = Objects.requireNonNull(familyId, "familyId");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.revokedAt = revokedAt;
        if (!expiresAt.isAfter(issuedAt)) {
            throw new DomainValidationException("expiresAt", "Refresh token must expire after it is issued.");
        }
        if (revokedAt != null && revokedAt.isBefore(issuedAt)) {
            throw new DomainValidationException("revokedAt", "Refresh token cannot be revoked before it is issued.");
        }
    }

    public static RefreshToken issue(
            RefreshTokenId id,
            UserId userId,
            RefreshTokenFamilyId familyId,
            RefreshTokenHash tokenHash,
            Instant issuedAt,
            Instant expiresAt) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, null);
    }

    public static RefreshToken rehydrate(
            RefreshTokenId id,
            UserId userId,
            RefreshTokenFamilyId familyId,
            RefreshTokenHash tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt) {
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, revokedAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Expiry is inclusive of the boundary: a token is dead at exactly {@code expiresAt}. */
    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }

    public boolean isUsableAt(Instant now) {
        return !isRevoked() && !isExpiredAt(now);
    }

    /** Revoking an already-revoked token keeps the original revocation instant. */
    public RefreshToken revokeAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isRevoked()) {
            return this;
        }
        return new RefreshToken(id, userId, familyId, tokenHash, issuedAt, expiresAt, now);
    }

    public RefreshTokenId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public RefreshTokenFamilyId familyId() {
        return familyId;
    }

    public RefreshTokenHash tokenHash() {
        return tokenHash;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> revokedAt() {
        return Optional.ofNullable(revokedAt);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RefreshToken that && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "RefreshToken[id=" + id + ", userId=" + userId + ", revoked=" + isRevoked() + "]";
    }
}
