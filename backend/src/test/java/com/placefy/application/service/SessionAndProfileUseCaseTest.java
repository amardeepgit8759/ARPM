package com.placefy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.AdminAccessRequiredException;
import com.placefy.application.error.InvalidCredentialsException;
import com.placefy.application.error.InvalidRefreshTokenException;
import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.ChangeMyPassword;
import com.placefy.application.port.in.ChangeMyPassword.ChangeMyPasswordCommand;
import com.placefy.application.port.in.GetAdminOverview;
import com.placefy.application.port.in.Login;
import com.placefy.application.port.in.Logout;
import com.placefy.application.port.in.Logout.LogoutCommand;
import com.placefy.application.port.in.RefreshSession.RefreshSessionCommand;
import com.placefy.application.port.in.UpdateMyProfile;
import com.placefy.application.port.in.UpdateMyProfile.UpdateMyProfileCommand;
import com.placefy.application.port.in.UserProfile;
import com.placefy.domain.DomainValidationException;
import com.placefy.domain.user.Role;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SessionAndProfileUseCaseTest extends AuthUseCaseFixture {

    private final Logout logout = new LogoutService(refreshTokens, tokenHasher, time);
    private final UpdateMyProfile updateMyProfile = new UpdateMyProfileService(users, time);
    private final ChangeMyPassword changeMyPassword =
            new ChangeMyPasswordService(users, refreshTokens, passwordHasher, sessions, time, ids);
    private final GetAdminOverview getAdminOverview = new GetAdminOverviewService(users);

    @Nested
    class Logging_out {

        @Test
        void theRefreshTokenStopsWorking() {
            AuthenticatedSession session = registerAda();

            logout.handle(new LogoutCommand(session.refreshToken()));

            assertThat(refreshTokens.liveTokens()).isEmpty();
            assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(session.refreshToken())))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }

        @Test
        @DisplayName("a rotated-away copy of the session dies with it, because the family is revoked")
        void revokesTheWholeFamily() {
            AuthenticatedSession registered = registerAda();
            AuthenticatedSession rotated =
                    refreshSession.handle(new RefreshSessionCommand(registered.refreshToken()));

            logout.handle(new LogoutCommand(rotated.refreshToken()));

            assertThat(refreshTokens.liveTokens()).isEmpty();
        }

        @Test
        @DisplayName("signing out on one device leaves the user's other sessions alone")
        void leavesOtherSessionsOfTheSameUserAlive() {
            AuthenticatedSession laptop = registerAda();
            AuthenticatedSession phone = login.handle(new Login.LoginCommand("ada@example.com", "correct-horse-battery"));

            logout.handle(new LogoutCommand(laptop.refreshToken()));

            assertThat(refreshSession.handle(new RefreshSessionCommand(phone.refreshToken())).user().email())
                    .isEqualTo("ada@example.com");
        }

        @Test
        @DisplayName("one user signing out never touches another user's session")
        void leavesOtherUsersAlone() {
            AuthenticatedSession ada = registerAda();
            AuthenticatedSession grace = register("Grace Hopper", "grace@example.com", "another-good-password");

            logout.handle(new LogoutCommand(ada.refreshToken()));

            assertThat(refreshTokens.liveTokens()).hasSize(1);
            assertThat(refreshTokens.liveTokens().get(0).userId().value()).isEqualTo(grace.user().id());
        }

        @Test
        void signingOutTwiceIsHarmless() {
            AuthenticatedSession session = registerAda();

            logout.handle(new LogoutCommand(session.refreshToken()));
            logout.handle(new LogoutCommand(session.refreshToken()));

            assertThat(refreshTokens.liveTokens()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "not-a-token-this-server-ever-issued"})
        @DisplayName("a missing or unknown token is a silent no-op")
        void unknownTokensAreANoOp(String presented) {
            registerAda();

            logout.handle(new LogoutCommand(presented));

            assertThat(refreshTokens.liveTokens()).hasSize(1);
        }
    }

    @Nested
    class Updating_the_profile {

        @Test
        void renamesTheCallerAndStampsTheUpdate() {
            AuthenticatedSession session = registerAda();
            time.advanceBy(Duration.ofMinutes(5));
            Instant later = NOW.plus(Duration.ofMinutes(5));

            UserProfile updated = updateMyProfile.handle(
                    new UpdateMyProfileCommand(UserId.of(session.user().id()), "  Ada   King  "));

            assertThat(updated.name()).isEqualTo("Ada King");
            User stored = users.findById(UserId.of(session.user().id())).orElseThrow();
            assertThat(stored.name().value()).isEqualTo("Ada King");
            assertThat(stored.updatedAt()).isEqualTo(later);
            assertThat(stored.createdAt()).isEqualTo(NOW);
        }

        @Test
        void rejectsAnInvalidName() {
            AuthenticatedSession session = registerAda();

            assertThatThrownBy(() -> updateMyProfile.handle(
                            new UpdateMyProfileCommand(UserId.of(session.user().id()), "A")))
                    .isInstanceOf(DomainValidationException.class);
            assertThat(users.findById(UserId.of(session.user().id())).orElseThrow().name().value())
                    .isEqualTo("Ada Lovelace");
        }

        @Test
        @DisplayName("renaming one user leaves every other user untouched")
        void isScopedToTheCaller() {
            AuthenticatedSession ada = registerAda();
            AuthenticatedSession grace = register("Grace Hopper", "grace@example.com", "another-good-password");

            updateMyProfile.handle(new UpdateMyProfileCommand(UserId.of(ada.user().id()), "Ada King"));

            assertThat(users.findById(UserId.of(grace.user().id())).orElseThrow().name().value())
                    .isEqualTo("Grace Hopper");
        }

        @Test
        void refusesAnIdThatMatchesNoUser() {
            assertThatThrownBy(() -> updateMyProfile.handle(
                            new UpdateMyProfileCommand(UserId.of(UUID.randomUUID()), "Ada King")))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class Changing_the_password {

        @Test
        void theNewPasswordWorksAndTheOldOneDoesNot() {
            AuthenticatedSession session = registerAda();

            changeMyPassword.handle(new ChangeMyPasswordCommand(
                    UserId.of(session.user().id()), "correct-horse-battery", "a-brand-new-passphrase"));

            assertThat(login.handle(new Login.LoginCommand("ada@example.com", "a-brand-new-passphrase"))
                            .user()
                            .email())
                    .isEqualTo("ada@example.com");
            assertThatThrownBy(() -> login.handle(new Login.LoginCommand("ada@example.com", "correct-horse-battery")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("every earlier session is revoked, and exactly one new one is issued")
        void revokesEveryOldSessionAndIssuesOneNew() {
            AuthenticatedSession laptop = registerAda();
            AuthenticatedSession phone = login.handle(new Login.LoginCommand("ada@example.com", "correct-horse-battery"));

            AuthenticatedSession fresh = changeMyPassword.handle(new ChangeMyPasswordCommand(
                    UserId.of(laptop.user().id()), "correct-horse-battery", "a-brand-new-passphrase"));

            assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(laptop.refreshToken())))
                    .isInstanceOf(InvalidRefreshTokenException.class);
            assertThatThrownBy(() -> refreshSession.handle(new RefreshSessionCommand(phone.refreshToken())))
                    .isInstanceOf(InvalidRefreshTokenException.class);
            assertThat(refreshSession.handle(new RefreshSessionCommand(fresh.refreshToken())).user().id())
                    .isEqualTo(laptop.user().id());
        }

        @Test
        @DisplayName("a stolen access token alone cannot change the password")
        void requiresTheCurrentPassword() {
            AuthenticatedSession session = registerAda();

            assertThatThrownBy(() -> changeMyPassword.handle(new ChangeMyPasswordCommand(
                            UserId.of(session.user().id()), "a-guess-at-the-password", "a-brand-new-passphrase")))
                    .isInstanceOf(InvalidCredentialsException.class);

            // Nothing moved: the session survives and the old password still works.
            assertThat(refreshTokens.liveTokens()).hasSize(1);
            assertThat(login.handle(new Login.LoginCommand("ada@example.com", "correct-horse-battery")))
                    .isNotNull();
        }

        @Test
        void theNewPasswordMustMeetThePolicy() {
            AuthenticatedSession session = registerAda();

            assertThatThrownBy(() -> changeMyPassword.handle(new ChangeMyPasswordCommand(
                            UserId.of(session.user().id()), "correct-horse-battery", "short")))
                    .isInstanceOf(DomainValidationException.class)
                    .hasMessageContaining("at least");
            assertThat(refreshTokens.liveTokens()).hasSize(1);
        }

        @Test
        @DisplayName("changing one user's password revokes none of another user's sessions")
        void isScopedToTheCaller() {
            AuthenticatedSession ada = registerAda();
            AuthenticatedSession grace = register("Grace Hopper", "grace@example.com", "another-good-password");

            changeMyPassword.handle(new ChangeMyPasswordCommand(
                    UserId.of(ada.user().id()), "correct-horse-battery", "a-brand-new-passphrase"));

            assertThat(refreshSession.handle(new RefreshSessionCommand(grace.refreshToken())).user().email())
                    .isEqualTo("grace@example.com");
            assertThat(login.handle(new Login.LoginCommand("grace@example.com", "another-good-password")))
                    .isNotNull();
        }

        @Test
        @DisplayName("another user's password does not authorise the change")
        void refusesAnotherUsersPassword() {
            AuthenticatedSession ada = registerAda();
            register("Grace Hopper", "grace@example.com", "another-good-password");

            assertThatThrownBy(() -> changeMyPassword.handle(new ChangeMyPasswordCommand(
                            UserId.of(ada.user().id()), "another-good-password", "a-brand-new-passphrase")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    class Admin_overview {

        @Test
        void countsStudentsForAnAdministrator() {
            registerAda();
            register("Grace Hopper", "grace@example.com", "another-good-password");
            UserId admin = storeAdmin();

            assertThat(getAdminOverview.handle(admin).studentCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("administrators are not counted as students")
        void doesNotCountAdmins() {
            UserId admin = storeAdmin();

            assertThat(getAdminOverview.handle(admin).studentCount()).isZero();
        }

        @Test
        @DisplayName("a student is refused on the stored role, whatever their token claims")
        void refusesAStudent() {
            AuthenticatedSession ada = registerAda();

            assertThatThrownBy(() -> getAdminOverview.handle(UserId.of(ada.user().id())))
                    .isInstanceOf(AdminAccessRequiredException.class);
        }

        @Test
        void refusesAnIdThatMatchesNoUser() {
            assertThatThrownBy(() -> getAdminOverview.handle(UserId.of(UUID.randomUUID())))
                    .isInstanceOf(UserNotFoundException.class);
        }

        /** Promotion is a database action (Role.java), so the test does what an operator would. */
        private UserId storeAdmin() {
            AuthenticatedSession registered = register("Site Admin", "admin@example.com", "an-admin-passphrase");
            User student = users.findById(UserId.of(registered.user().id())).orElseThrow();
            users.save(User.rehydrate(
                    student.id(),
                    student.name(),
                    student.email(),
                    student.passwordHash(),
                    Role.ADMIN,
                    student.createdAt(),
                    student.updatedAt()));
            return student.id();
        }
    }
}
