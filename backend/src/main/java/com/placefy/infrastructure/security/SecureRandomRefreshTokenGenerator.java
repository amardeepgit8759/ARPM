package com.placefy.infrastructure.security;

import com.placefy.application.port.out.RefreshTokenGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class SecureRandomRefreshTokenGenerator implements RefreshTokenGenerator {

    /** 256 bits. Guessing one is not a threat model; stealing one is, which rotation handles. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
