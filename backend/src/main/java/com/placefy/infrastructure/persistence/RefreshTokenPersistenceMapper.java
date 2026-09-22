package com.placefy.infrastructure.persistence;

import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.session.RefreshTokenHash;
import com.placefy.domain.session.RefreshTokenId;
import com.placefy.domain.user.UserId;

final class RefreshTokenPersistenceMapper {

    private RefreshTokenPersistenceMapper() {}

    static RefreshTokenJpaEntity toEntity(RefreshToken token) {
        return new RefreshTokenJpaEntity(
                token.id().value(),
                token.userId().value(),
                token.familyId().value(),
                token.tokenHash().value(),
                token.issuedAt(),
                token.expiresAt(),
                token.revokedAt().orElse(null));
    }

    static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.rehydrate(
                RefreshTokenId.of(entity.getId()),
                UserId.of(entity.getUserId()),
                RefreshTokenFamilyId.of(entity.getFamilyId()),
                RefreshTokenHash.of(entity.getTokenHash()),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt());
    }
}
