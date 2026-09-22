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

    public List<RefreshToken> all() {
        return new ArrayList<>(byId.values());
    }

    public List<RefreshToken> liveTokens() {
        return byId.values().stream().filter(token -> !token.isRevoked()).toList();
    }
}
