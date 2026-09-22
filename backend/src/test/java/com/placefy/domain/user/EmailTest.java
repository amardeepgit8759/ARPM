package com.placefy.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    @DisplayName("lower-cases and trims so case variants collide on the unique index")
    void normalises() {
        assertThat(Email.of("  Ada.Lovelace@Example.COM ").value()).isEqualTo("ada.lovelace@example.com");
    }

    @Test
    void equalsIgnoresOriginalCasing() {
        assertThat(Email.of("A@B.COM")).isEqualTo(Email.of("a@b.com"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "no-at-sign", "two@@at.com", "no@domain", "spaces in@name.com", "@nolocal.com"})
    void rejectsMalformedAddresses(String candidate) {
        assertThatThrownBy(() -> Email.of(candidate))
                .isInstanceOf(DomainValidationException.class)
                .extracting(e -> ((DomainValidationException) e).field())
                .isEqualTo("email");
    }

    @Test
    void rejectsAddressesLongerThanTheRfcLimit() {
        String tooLong = "a".repeat(250) + "@example.com";
        assertThatThrownBy(() -> Email.of(tooLong)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("the canonical constructor refuses an un-normalised value")
    void canonicalConstructorRefusesUnnormalised() {
        assertThatThrownBy(() -> new Email("Ada@Example.com")).isInstanceOf(DomainValidationException.class);
    }
}
