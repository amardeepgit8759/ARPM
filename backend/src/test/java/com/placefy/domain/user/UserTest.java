package com.placefy.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final PasswordHash HASH =
            PasswordHash.of("$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012345");

    @Test
    @DisplayName("registration always produces a STUDENT")
    void registrationProducesAStudent() {
        assertThat(register().role()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("no registration input can produce an ADMIN")
    void registrationCanNeverProduceAnAdmin() {
        // ADMIN gates content publishing. `register` takes no role argument at all, so there is
        // no value a caller could supply to obtain one; promotion is a database action only.
        assertThat(register().role()).isNotEqualTo(Role.ADMIN);
        assertThat(User.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("register"))
                .allSatisfy(method -> assertThat(method.getParameterTypes()).doesNotContain(Role.class));
    }

    @Test
    @DisplayName("a freshly registered user was created and updated at the same instant")
    void timestampsStartEqual() {
        User user = register();
        assertThat(user.createdAt()).isEqualTo(AT);
        assertThat(user.updatedAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("registering twice from the same inputs produces identical state")
    void registrationIsDeterministic() {
        UserId id = UserId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        User first = User.register(id, FullName.of("Ada Lovelace"), Email.of("ada@example.com"), HASH, AT);
        User second = User.register(id, FullName.of("Ada Lovelace"), Email.of("ada@example.com"), HASH, AT);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.name()).isEqualTo(first.name());
        assertThat(second.email()).isEqualTo(first.email());
        assertThat(second.passwordHash()).isEqualTo(first.passwordHash());
        assertThat(second.role()).isEqualTo(first.role());
        assertThat(second.createdAt()).isEqualTo(first.createdAt());
        assertThat(second.updatedAt()).isEqualTo(first.updatedAt());
    }

    @Test
    void rejectsAnUpdateTimestampBeforeCreation() {
        assertThatThrownBy(() -> User.rehydrate(
                        UserId.of(UUID.randomUUID()),
                        FullName.of("Ada Lovelace"),
                        Email.of("ada@example.com"),
                        HASH,
                        Role.STUDENT,
                        AT,
                        AT.minusSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("identity is the id alone, not the mutable attributes")
    void identityIsTheIdAlone() {
        UserId id = UserId.of(UUID.randomUUID());
        User original = User.register(id, FullName.of("Ada Lovelace"), Email.of("ada@example.com"), HASH, AT);
        User renamed = User.rehydrate(
                id, FullName.of("Ada King"), Email.of("ada.king@example.com"), HASH, Role.STUDENT, AT, AT);
        assertThat(renamed).isEqualTo(original);
    }

    @Test
    void toStringDoesNotLeakTheHash() {
        assertThat(register().toString()).doesNotContain("$2a$12$");
    }

    private User register() {
        return User.register(
                UserId.of(UUID.randomUUID()), FullName.of("Ada Lovelace"), Email.of("ada@example.com"), HASH, AT);
    }
}
