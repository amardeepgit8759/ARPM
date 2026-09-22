package com.placefy.application.port.out;

import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.session.RefreshTokenHash;
import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByHash(RefreshTokenHash hash);

    /**
     * Revokes every token in a rotation chain, used when a token is presented twice.
     *
     * @return how many tokens this revoked, so callers can assert the blast radius in tests
     */
    int revokeFamily(RefreshTokenFamilyId familyId, Instant revokedAt);
}
