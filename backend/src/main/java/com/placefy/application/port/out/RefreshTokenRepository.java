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

    /**
     * Revokes every live token this user holds, across all families. Used when the password
     * changes, which must end every session the old password opened.
     *
     * @return how many tokens this revoked
     */
    int revokeAllForUser(com.placefy.domain.user.UserId userId, Instant revokedAt);

    /**
     * Every session this user has, newest first, for a data export.
     *
     * <p>Returns the tokens themselves; it is the export projection's job to drop the hashes.
     * Keeping that decision in one visible place beats a repository that quietly returns
     * different shapes to different callers.
     */
    java.util.List<RefreshToken> findByUser(com.placefy.domain.user.UserId userId);
}
