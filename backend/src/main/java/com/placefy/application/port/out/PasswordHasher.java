package com.placefy.application.port.out;

import com.placefy.domain.user.PasswordHash;
import com.placefy.domain.user.RawPassword;

public interface PasswordHasher {

    /**
     * Hashes a password that has passed the domain policy. Taking {@link RawPassword} rather than
     * a String is deliberate: policy is enforced exactly where a password is <em>set</em>.
     */
    PasswordHash hash(RawPassword password);

    /**
     * Verifies a candidate. Takes a plain String, not {@link RawPassword}, because verification
     * must not apply today's policy to a password stored under yesterday's — a user whose
     * existing password is shorter than the current minimum must still be able to log in.
     */
    boolean matches(String candidatePassword, PasswordHash hash);

    /**
     * A real hash of a value nobody knows, used to spend the same CPU time when the account does
     * not exist. Without it, login answers faster for unknown addresses than for known ones and
     * becomes an account-enumeration oracle.
     */
    PasswordHash decoyHash();
}
