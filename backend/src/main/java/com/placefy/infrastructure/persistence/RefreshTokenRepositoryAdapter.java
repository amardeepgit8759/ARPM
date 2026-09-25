package com.placefy.infrastructure.persistence;

import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.session.RefreshTokenHash;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public RefreshToken save(RefreshToken token) {
        jpa.save(RefreshTokenPersistenceMapper.toEntity(token));
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByHash(RefreshTokenHash hash) {
        return jpa.findByTokenHash(hash.value()).map(RefreshTokenPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<RefreshToken> findByUser(com.placefy.domain.user.UserId userId) {
        return jpa.findByUserIdOrderByIssuedAtDesc(userId.value()).stream()
                .map(RefreshTokenPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public int revokeFamily(RefreshTokenFamilyId familyId, Instant revokedAt) {
        return jpa.revokeFamily(familyId.value(), revokedAt);
    }

    @Override
    @Transactional
    public int revokeAllForUser(com.placefy.domain.user.UserId userId, Instant revokedAt) {
        return jpa.revokeAllForUser(userId.value(), revokedAt);
    }
}
