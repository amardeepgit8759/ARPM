package com.placefy.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RawPasswordTest {

    @Test
    void acceptsAPasswordAtTheMinimumLength() {
        assertThatCode(() -> RawPassword.of("a".repeat(RawPassword.MIN_LENGTH))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAPasswordOneCharacterShort() {
        assertThatThrownBy(() -> RawPassword.of("a".repeat(RawPassword.MIN_LENGTH - 1)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining(String.valueOf(RawPassword.MIN_LENGTH));
    }

    @Test
    @DisplayName("rejects rather than truncates past BCrypt's 72-byte ceiling")
    void rejectsPasswordsBeyondTheBcryptCeiling() {
        assertThatThrownBy(() -> RawPassword.of("a".repeat(RawPassword.MAX_BYTES + 1)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("counts bytes, not characters, so multi-byte passwords are measured correctly")
    void measuresLengthInUtf8Bytes() {
        // Each emoji is 4 UTF-8 bytes: 19 of them is 76 bytes but only 38 chars.
        String multiByte = "\uD83D\uDE00".repeat(19);
        assertThat(multiByte.length()).isLessThan(RawPassword.MAX_BYTES);
        assertThatThrownBy(() -> RawPassword.of(multiByte)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("never leaks the secret through toString")
    void toStringIsRedacted() {
        RawPassword password = RawPassword.of("correct horse battery staple");
        assertThat(password.toString()).doesNotContain("correct").isEqualTo("RawPassword[REDACTED]");
    }
}
