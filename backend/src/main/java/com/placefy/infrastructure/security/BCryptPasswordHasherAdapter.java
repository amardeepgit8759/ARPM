package com.placefy.infrastructure.security;

import com.placefy.application.port.out.PasswordHasher;
import com.placefy.domain.user.PasswordHash;
import com.placefy.domain.user.RawPassword;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHasherAdapter implements PasswordHasher {

    /** Cost 12: roughly a quarter-second per hash on current hardware. */
    private static final int COST = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(COST, new SecureRandom());

    /**
     * Hashed once at startup from a value that exists only in this process's memory and is
     * discarded immediately. Verifying against it costs exactly what verifying a real password
     * costs, which is the entire point.
     */
    private final PasswordHash decoy = PasswordHash.of(encoder.encode(newThrowawaySecret()));

    @Override
    public PasswordHash hash(RawPassword password) {
        return PasswordHash.of(encoder.encode(password.value()));
    }

    @Override
    public boolean matches(String candidatePassword, PasswordHash hash) {
        if (candidatePassword == null) {
            return false;
        }
        return encoder.matches(candidatePassword, hash.value());
    }

    @Override
    public PasswordHash decoyHash() {
        return decoy;
    }

    private static String newThrowawaySecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
