package com.placefy.infrastructure.security;

import com.placefy.application.port.out.RefreshTokenHasher;
import com.placefy.domain.session.RefreshTokenHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
class Sha256RefreshTokenHasher implements RefreshTokenHasher {

    @Override
    public RefreshTokenHash hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return RefreshTokenHash.of(HexFormat.of().formatHex(hashed));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
