package com.placefy.domain.user;

import com.placefy.domain.DomainValidationException;
import java.nio.charset.StandardCharsets;

/**
 * A password as the user typed it, validated but never stored.
 *
 * <p>Not a record on purpose: the generated {@code toString} and {@code equals} would put a
 * live secret into log lines and test failure output.
 *
 * <p>The 72-byte ceiling is not a policy choice. BCrypt silently truncates input beyond 72
 * bytes, so a longer password would authenticate on its first 72 bytes and give the user a
 * false sense of strength. Rejecting is honest; truncating is not.
 */
public final class RawPassword {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;

    private final String value;

    private RawPassword(String value) {
        this.value = value;
    }

    public static RawPassword of(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new DomainValidationException("password", "Password is required.");
        }
        if (raw.length() < MIN_LENGTH) {
            throw new DomainValidationException(
                    "password", "Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new DomainValidationException(
                    "password", "Password must be at most " + MAX_BYTES + " bytes.");
        }
        return new RawPassword(raw);
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "RawPassword[REDACTED]";
    }
}
