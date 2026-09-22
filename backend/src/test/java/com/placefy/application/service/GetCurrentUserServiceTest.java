package com.placefy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.AuthenticatedSession;
import com.placefy.application.port.in.UserProfile;
import com.placefy.domain.user.UserId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetCurrentUserServiceTest extends AuthUseCaseFixture {

    @Test
    void returnsTheProfileOfTheRequestedUser() {
        AuthenticatedSession registered = registerAda();

        UserProfile profile = getCurrentUser.handle(UserId.of(registered.user().id()));

        assertThat(profile.email()).isEqualTo("ada@example.com");
        assertThat(profile.name()).isEqualTo("Ada Lovelace");
        assertThat(profile.role()).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("one user's id never returns another user's data")
    void neverReturnsAnotherUsersProfile() {
        AuthenticatedSession ada = registerAda();
        AuthenticatedSession grace = register("Grace Hopper", "grace@example.com", "another-good-password");

        assertThat(getCurrentUser.handle(UserId.of(ada.user().id())).email()).isEqualTo("ada@example.com");
        assertThat(getCurrentUser.handle(UserId.of(grace.user().id())).email()).isEqualTo("grace@example.com");
        assertThat(ada.user().id()).isNotEqualTo(grace.user().id());
    }

    @Test
    void rejectsAnIdThatMatchesNoUser() {
        registerAda();

        assertThatThrownBy(() -> getCurrentUser.handle(UserId.of(UUID.randomUUID())))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("the profile carries no password material")
    void theProfileExposesNoSecrets() {
        AuthenticatedSession registered = registerAda();

        assertThat(getCurrentUser.handle(UserId.of(registered.user().id())).toString())
                .doesNotContain("hashed:")
                .doesNotContain("correct-horse-battery");
    }
}
