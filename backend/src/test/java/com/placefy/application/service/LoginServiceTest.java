package com.placefy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.InvalidCredentialsException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.Login.LoginCommand;
import com.placefy.domain.session.RefreshToken;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoginServiceTest extends AuthUseCaseFixture {

    @Test
    void authenticatesWithTheCorrectPassword() {
        AuthenticatedSession registered = registerAda();

        AuthenticatedSession session = login.handle(new LoginCommand("ada@example.com", "correct-horse-battery"));

        assertThat(session.user()).isEqualTo(registered.user());
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotEqualTo(registered.refreshToken());
    }

    @Test
    void emailMatchingIgnoresCaseAndSurroundingWhitespace() {
        registerAda();

        assertThat(login.handle(new LoginCommand("  ADA@Example.COM ", "correct-horse-battery")).user().email())
                .isEqualTo("ada@example.com");
    }

    @Test
    void rejectsTheWrongPassword() {
        registerAda();
        int tokensBefore = refreshTokens.all().size();

        assertThatThrownBy(() -> login.handle(new LoginCommand("ada@example.com", "wrong-password-here")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(refreshTokens.all()).hasSize(tokensBefore);
    }

    @Test
    @DisplayName("an unknown address costs the same hash work as a known one")
    void spendsTheSameWorkOnAnUnknownAddress() {
        registerAda();
        int verificationsBefore = passwordHasher.verifications();

        assertThatThrownBy(() -> login.handle(new LoginCommand("nobody@example.com", "correct-horse-battery")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(passwordHasher.verifications())
                .as("a miss must still perform one verification, against the decoy hash")
                .isEqualTo(verificationsBefore + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "", "   ", "@nolocal.com"})
    @DisplayName("a malformed address is a credential failure, not a validation error")
    void malformedAddressesLookExactlyLikeUnknownOnes(String malformed) {
        registerAda();
        int verificationsBefore = passwordHasher.verifications();

        assertThatThrownBy(() -> login.handle(new LoginCommand(malformed, "correct-horse-battery")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(passwordHasher.verifications()).isEqualTo(verificationsBefore + 1);
    }

    @Test
    @DisplayName("each login opens its own rotation family, so revoking one does not end the other")
    void eachLoginStartsANewFamily() {
        registerAda();
        login.handle(new LoginCommand("ada@example.com", "correct-horse-battery"));
        login.handle(new LoginCommand("ada@example.com", "correct-horse-battery"));

        assertThat(refreshTokens.all().stream().map(RefreshToken::familyId).distinct()).hasSize(3);
    }

    @Test
    void theIssuedTokenIsDatedFromTheCurrentInstant() {
        registerAda();
        time.advanceBy(Duration.ofHours(6));

        AuthenticatedSession session = login.handle(new LoginCommand("ada@example.com", "correct-horse-battery"));

        assertThat(session.refreshTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(6)).plus(REFRESH_TTL));
    }

    @Test
    void neverAuthenticatesAgainstAnEmptyStore() {
        assertThatThrownBy(() -> login.handle(new LoginCommand("ada@example.com", "correct-horse-battery")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
