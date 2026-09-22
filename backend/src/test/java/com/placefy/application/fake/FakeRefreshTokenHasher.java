package com.placefy.application.fake;

import com.placefy.application.port.out.RefreshTokenHasher;
import com.placefy.domain.session.RefreshTokenHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Real SHA-256: it is deterministic and fast, so there is nothing to gain by faking it. */
public class FakeRefreshTokenHasher implements RefreshTokenHasher {

    @Override
    public RefreshTokenHash hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return RefreshTokenHash.of(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
