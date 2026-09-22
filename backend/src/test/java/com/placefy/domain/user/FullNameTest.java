package com.placefy.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class FullNameTest {

    @Test
    void collapsesInternalWhitespaceAndTrims() {
        assertThat(FullName.of("  Ada   Lovelace ").value()).isEqualTo("Ada Lovelace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "A"})
    void rejectsBlankOrTooShortNames(String candidate) {
        assertThatThrownBy(() -> FullName.of(candidate))
                .isInstanceOf(DomainValidationException.class)
                .extracting(e -> ((DomainValidationException) e).field())
                .isEqualTo("name");
    }

    @Test
    void rejectsNamesBeyondTheColumnWidth() {
        assertThatThrownBy(() -> FullName.of("a".repeat(121))).isInstanceOf(DomainValidationException.class);
    }
}
