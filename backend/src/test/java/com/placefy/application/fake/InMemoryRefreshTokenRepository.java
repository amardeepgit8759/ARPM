package com.placefy.application.fake;

import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.session.RefreshTokenFamilyId;
import com.placefy.domain.session.RefreshTokenHash;
import com.placefy.domain.session.RefreshTokenId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryRefreshTokenRepository implements RefreshTokenRepository {

    private final Map<RefreshTokenId, RefreshToken> byId = new LinkedHashMap<>();

    @Override
    public RefreshToken save(RefreshToken token) {
        byId.put(token.id(), token);
        return token;
    }

    @Override
    public Optional<RefreshToken> findByHash(RefreshTokenHash hash) {
        return byId.values().stream().filter(token -> token.tokenHash().equals(hash)).findFirst();
    }

    @Override
    public int revokeFamily(RefreshTokenFamilyId familyId, Instant revokedAt) {
        List<RefreshToken> family = byId.values().stream()
                .filter(token -> token.familyId().equals(familyId))
                .filter(token -> !token.isRevoked())
                .toList();
        family.forEach(token -> byId.put(token.id(), token.revokeAt(revokedAt)));
        return family.size();
    }

    @Override
    public int revokeAllForUser(com.placefy.domain.user.UserId userId, Instant revokedAt) {
        List<RefreshToken> live = byId.values().stream()
                .filter(token -> token.userId().equals(userId))
                .filter(token -> !token.isRevoked())
                .toList();
        live.forEach(token -> byId.put(token.id(), token.revokeAt(revokedAt)));
        return live.size();
    }

    @Override
    public List<RefreshToken> findByUser(com.placefy.domain.user.UserId userId) {
        // Newest first, matching the real adapter's ordering so an export test that passes here
        // is not passing for a reason the database would contradict.
        return byId.values().stream()
                .filter(token -> token.userId().equals(userId))
                .sorted((a, b) -> b.issuedAt().compareTo(a.issuedAt()))
                .toList();
    }

    public List<RefreshToken> all() {
        return new ArrayList<>(byId.values());
    }

    public List<RefreshToken> liveTokens() {
        return byId.values().stream().filter(token -> !token.isRevoked()).toList();
    }
}
