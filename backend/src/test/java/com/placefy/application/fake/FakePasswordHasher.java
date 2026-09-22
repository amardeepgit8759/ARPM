package com.placefy.application.fake;

import com.placefy.application.port.out.PasswordHasher;
import com.placefy.domain.user.PasswordHash;
import com.placefy.domain.user.RawPassword;

/**
 * Reversible on purpose: a use-case test should fail because the logic is wrong, not because
 * BCrypt was slow. Counting verifications is what lets the enumeration-defence test assert that
 * a miss costs the same work as a hit.
 */
public class FakePasswordHasher implements PasswordHasher {

    private static final String PREFIX = "hashed:";
    private static final PasswordHash DECOY = PasswordHash.of(PREFIX + "$$decoy$$");

    private int verifications = 0;

    @Override
    public PasswordHash hash(RawPassword password) {
        return PasswordHash.of(PREFIX + password.value());
    }

    @Override
    public boolean matches(String candidatePassword, PasswordHash hash) {
        verifications++;
        return hash.value().equals(PREFIX + candidatePassword);
    }

    @Override
    public PasswordHash decoyHash() {
        return DECOY;
    }

    public int verifications() {
        return verifications;
    }
}
