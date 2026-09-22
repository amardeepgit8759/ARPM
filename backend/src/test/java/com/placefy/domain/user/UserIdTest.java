package com.placefy.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserIdTest {

    @Test
    void parsesAWellFormedUuid() {
        UUID uuid = UUID.randomUUID();
        assertThat(UserId.parse(" " + uuid + " ")).isEqualTo(UserId.of(uuid));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not-a-uuid", "11111111-1111-1111-1111"})
    void rejectsAnythingElse(String candidate) {
        assertThatThrownBy(() -> UserId.parse(candidate)).isInstanceOf(DomainValidationException.class);
    }
}
