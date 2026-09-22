package com.placefy.domain.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.stream.Stream;

class RefreshTokenHashTest {

    private static final String VALID = "0123456789abcdef".repeat(4);

    @Test
    void acceptsSixtyFourLowercaseHexCharacters() {
        assertThat(RefreshTokenHash.of(VALID).value()).hasSize(64);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("malformedHashes")
    void rejectsAnythingElse(String candidate) {
        assertThatThrownBy(() -> RefreshTokenHash.of(candidate)).isInstanceOf(DomainValidationException.class);
    }

    static Stream<String> malformedHashes() {
        return Stream.of(
                "abc", // too short
                VALID + "0", // too long
                VALID.toUpperCase(), // uppercase hex would break lookup equality
                "g".repeat(64)); // not hex
    }

    @Test
    void toStringShowsOnlyAPrefix() {
        assertThat(RefreshTokenHash.of(VALID).toString()).doesNotContain(VALID).contains("01234567");
    }
}
