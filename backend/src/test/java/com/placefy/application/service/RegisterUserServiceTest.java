package com.placefy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.EmailAlreadyRegisteredException;
import com.placefy.application.fake.FakeAccessTokenIssuer;
import com.placefy.application.fake.SequentialIdGenerator;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.RegisterUser.RegisterUserCommand;
import com.placefy.domain.DomainValidationException;
import com.placefy.domain.session.RefreshToken;
import com.placefy.domain.user.Email;
import com.placefy.domain.user.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegisterUserServiceTest extends AuthUseCaseFixture {

    @Test
    void registersAStudentAndReturnsTheirProfile() {
        AuthenticatedSession session = registerAda();

        assertThat(session.user().name()).isEqualTo("Ada Lovelace");
        assertThat(session.user().email()).isEqualTo("ada@example.com");
        assertThat(session.user().role()).isEqualTo("STUDENT");
        assertThat(session.user().createdAt()).isEqualTo(NOW);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("identifiers come from the generator, so a run is fully reproducible")
    void assignsIdentifiersInAStableOrder() {
        AuthenticatedSession session = registerAda();

        assertThat(session.user().id()).isEqualTo(SequentialIdGenerator.nth(1));
        RefreshToken stored = refreshTokens.all().get(0);
        assertThat(stored.familyId().value()).isEqualTo(SequentialIdGenerator.nth(2));
        assertThat(stored.id().value()).isEqualTo(SequentialIdGenerator.nth(3));
    }

    @Test
    void storesTheHashAndNeverTheRawPassword() {
        registerAda();

        var stored = users.findByEmail(Email.of("ada@example.com")).orElseThrow();
        assertThat(stored.passwordHash().value()).isEqualTo("hashed:correct-horse-battery");
        assertThat(stored.passwordHash().value()).doesNotStartWith("correct-horse-battery");
    }

    @Test
    @DisplayName("the refresh token is stored hashed; the raw secret is returned once and not kept")
    void storesTheRefreshTokenHashedOnly() {
        AuthenticatedSession session = registerAda();

        RefreshToken stored = refreshTokens.all().get(0);
        assertThat(stored.tokenHash()).isEqualTo(tokenHasher.hash(session.refreshToken()));
        assertThat(stored.tokenHash().value()).isNotEqualTo(session.refreshToken());
    }

    @Test
    void sessionLifetimesAreDerivedFromTheSuppliedInstant() {
        AuthenticatedSession session = registerAda();

        assertThat(session.accessTokenExpiresAt()).isEqualTo(NOW.plus(FakeAccessTokenIssuer.TTL));
        assertThat(session.refreshTokenExpiresAt()).isEqualTo(NOW.plus(REFRESH_TTL));
    }

    @Test
    void rejectsAnEmailThatIsAlreadyRegistered() {
        registerAda();

        assertThatThrownBy(() -> register("Ada Byron", "ada@example.com", "another-password"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a duplicate differing only in case is still a duplicate")
    void duplicateDetectionIsCaseInsensitive() {
        registerAda();

        assertThatThrownBy(() -> register("Ada Byron", "ADA@Example.COM", "another-password"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void aRejectedRegistrationLeavesNoRefreshToken() {
        registerAda();
        int tokensAfterFirst = refreshTokens.all().size();

        assertThatThrownBy(() -> register("Ada Byron", "ada@example.com", "another-password"))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(refreshTokens.all()).hasSize(tokensAfterFirst);
    }

    @ParameterizedTest(name = "{2} is rejected")
    @CsvSource({
        "A, ada@example.com, correct-horse-battery, name",
        "Ada Lovelace, not-an-email, correct-horse-battery, email",
        "Ada Lovelace, ada@example.com, short, password"
    })
    void rejectsInvalidInputBeforeTouchingStorage(String name, String email, String password, String field) {
        assertThatThrownBy(() -> registerUser.handle(new RegisterUserCommand(name, email, password)))
                .isInstanceOf(DomainValidationException.class)
                .extracting(e -> ((DomainValidationException) e).field())
                .isEqualTo(field);
        assertThat(users.count()).isZero();
    }

    @Test
    @DisplayName("the returned profile can be read back by the current-user query")
    void theRegisteredUserIsImmediatelyReadable() {
        AuthenticatedSession session = registerAda();

        assertThat(getCurrentUser.handle(UserId.of(session.user().id()))).isEqualTo(session.user());
    }
}
