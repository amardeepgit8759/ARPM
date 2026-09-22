package com.placefy.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class PasswordHashTest {

    private static final String BCRYPT = "$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012345";

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsBlankHashes(String candidate) {
        assertThatThrownBy(() -> PasswordHash.of(candidate)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void equalityIsByValue() {
        assertThat(PasswordHash.of(BCRYPT)).isEqualTo(PasswordHash.of(BCRYPT));
    }

    @Test
    void toStringIsRedacted() {
        assertThat(PasswordHash.of(BCRYPT).toString()).doesNotContain(BCRYPT).isEqualTo("PasswordHash[REDACTED]");
    }
}
