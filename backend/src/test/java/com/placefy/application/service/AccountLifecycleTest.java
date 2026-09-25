package com.placefy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.InvalidCredentialsException;
import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.DeleteMyAccount;
import com.placefy.application.port.in.DeleteMyAccount.DeleteMyAccountCommand;
import com.placefy.application.port.in.ExportMyData;
import com.placefy.application.port.in.UserDataExport;
import com.placefy.domain.user.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AccountLifecycleTest extends AuthUseCaseFixture {

    private final ExportMyData exportMyData = new ExportMyDataService(users, refreshTokens, time);
    private final DeleteMyAccount deleteMyAccount = new DeleteMyAccountService(users, passwordHasher);

    @Nested
    class Export {

        @Test
        void exportsTheUsersOwnProfile() {
            AuthenticatedSession session = registerAda();

            UserDataExport export = exportMyData.handle(UserId.of(session.user().id()));

            assertThat(export.user().email()).isEqualTo("ada@example.com");
            assertThat(export.user().name()).isEqualTo("Ada Lovelace");
            assertThat(export.exportedAt()).isEqualTo(NOW);
            assertThat(export.formatVersion()).isEqualTo(UserDataExport.FORMAT_VERSION);
        }

        @Test
        @DisplayName("sessions are exported as metadata, never as usable credentials")
        void exportsSessionMetadataWithoutTokenHashes() {
            AuthenticatedSession session = registerAda();

            UserDataExport export = exportMyData.handle(UserId.of(session.user().id()));

            assertThat(export.sessions()).hasSize(1);
            assertThat(export.sessions().get(0).issuedAt()).isEqualTo(NOW);
            assertThat(export.sessions().get(0).revoked()).isFalse();
            // The record has no field that could carry a hash. This asserts the shape, so adding
            // one would break a test rather than silently widening the export.
            assertThat(export.toString())
                    .doesNotContain(session.refreshToken())
                    .doesNotContain("hash");
        }

        @Test
        @DisplayName("one user's export never contains another user's data")
        void exportIsScopedToItsSubject() {
            AuthenticatedSession ada = registerAda();
            AuthenticatedSession grace = register("Grace Hopper", "grace@example.com", "another-good-password");

            UserDataExport adaExport = exportMyData.handle(UserId.of(ada.user().id()));

            assertThat(adaExport.user().email()).isEqualTo("ada@example.com");
            assertThat(adaExport.toString()).doesNotContain("grace@example.com");
            assertThat(adaExport.sessions()).hasSize(1);
            assertThat(grace.user().id()).isNotEqualTo(ada.user().id());
        }

        @Test
        void refusesAnIdThatMatchesNoUser() {
            assertThatThrownBy(() -> exportMyData.handle(UserId.of(UUID.randomUUID())))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class Deletion {

        @Test
        void deletesTheAccountWhenThePasswordIsCorrect() {
            AuthenticatedSession session = registerAda();

            deleteMyAccount.handle(new DeleteMyAccountCommand(
                    UserId.of(session.user().id()), "correct-horse-battery"));

            assertThat(users.count()).isZero();
        }

        @Test
        @DisplayName("a valid token is not enough; the password is a second factor against a stolen session")
        void refusesTheWrongPassword() {
            AuthenticatedSession session = registerAda();

            assertThatThrownBy(() -> deleteMyAccount.handle(
                            new DeleteMyAccountCommand(UserId.of(session.user().id()), "wrong-password-here")))
                    .isInstanceOf(InvalidCredentialsException.class);

            assertThat(users.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("another user's password does not unlock this account")
        void refusesAnotherUsersPassword() {
            AuthenticatedSession ada = registerAda();
            register("Grace Hopper", "grace@example.com", "another-good-password");

            assertThatThrownBy(() -> deleteMyAccount.handle(new DeleteMyAccountCommand(
                            UserId.of(ada.user().id()), "another-good-password")))
                    .isInstanceOf(InvalidCredentialsException.class);

            assertThat(users.count()).isEqualTo(2);
        }

        @Test
        void deletesOnlyTheNamedAccount() {
            AuthenticatedSession ada = registerAda();
            register("Grace Hopper", "grace@example.com", "another-good-password");

            deleteMyAccount.handle(
                    new DeleteMyAccountCommand(UserId.of(ada.user().id()), "correct-horse-battery"));

            assertThat(users.count()).isEqualTo(1);
        }

        @Test
        void refusesAnIdThatMatchesNoUser() {
            assertThatThrownBy(() -> deleteMyAccount.handle(
                            new DeleteMyAccountCommand(UserId.of(UUID.randomUUID()), "anything")))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("a deleted user cannot be read back")
        void deletionIsFinal() {
            AuthenticatedSession session = registerAda();
            UserId id = UserId.of(session.user().id());

            deleteMyAccount.handle(new DeleteMyAccountCommand(id, "correct-horse-battery"));

            assertThatThrownBy(() -> exportMyData.handle(id)).isInstanceOf(UserNotFoundException.class);
        }
    }
}
