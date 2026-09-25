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
    @DisplayName("renaming changes only the name and the update instant")
    void renameKeepsEverythingElse() {
        User original = register();
        Instant later = AT.plusSeconds(60);

        User renamed = original.rename(FullName.of("Ada King"), later);

        assertThat(renamed.name().value()).isEqualTo("Ada King");
        assertThat(renamed.id()).isEqualTo(original.id());
        assertThat(renamed.email()).isEqualTo(original.email());
        assertThat(renamed.passwordHash()).isEqualTo(original.passwordHash());
        assertThat(renamed.role()).isEqualTo(original.role());
        assertThat(renamed.createdAt()).isEqualTo(AT);
        assertThat(renamed.updatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("changing the password replaces only the hash and the update instant")
    void changePasswordKeepsEverythingElse() {
        User original = register();
        PasswordHash newHash =
                PasswordHash.of("$2a$12$ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqponmlkjihgfedcba987654");
        Instant later = AT.plusSeconds(60);

        User changed = original.changePassword(newHash, later);

        assertThat(changed.passwordHash()).isEqualTo(newHash);
        assertThat(changed.name()).isEqualTo(original.name());
        assertThat(changed.role()).isEqualTo(Role.STUDENT);
        assertThat(changed.updatedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("neither edit can move the update instant before creation")
    void editsCannotPrecedeCreation() {
        User original = register();
        assertThatThrownBy(() -> original.rename(FullName.of("Ada King"), AT.minusSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> original.changePassword(HASH, AT.minusSeconds(1)))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("no edit available on the aggregate can change the role")
    void editsNeverChangeTheRole() {
        User student = register();
        assertThat(student.isAdmin()).isFalse();
        assertThat(student.rename(FullName.of("Ada King"), AT).role()).isEqualTo(Role.STUDENT);
        assertThat(student.changePassword(HASH, AT).role()).isEqualTo(Role.STUDENT);
        assertThat(User.class.getDeclaredMethods())
                .filteredOn(method -> !method.getName().equals("rehydrate"))
                .allSatisfy(method -> assertThat(method.getParameterTypes()).doesNotContain(Role.class));
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
