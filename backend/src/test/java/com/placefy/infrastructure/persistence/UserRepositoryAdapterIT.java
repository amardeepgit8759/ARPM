package com.placefy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.application.error.EmailAlreadyRegisteredException;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.Email;
import com.placefy.domain.user.FullName;
import com.placefy.domain.user.PasswordHash;
import com.placefy.domain.user.Role;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import com.placefy.support.PostgresIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserRepositoryAdapterIT extends PostgresIntegrationTest {

    private static final PasswordHash HASH =
            PasswordHash.of("$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012345");

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearTables() {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, users CASCADE");
    }

    @Test
    void savesAndReadsBackAUserUnchanged() {
        User saved = users.save(ada(Instant.parse("2026-01-15T10:00:00Z")));

        User loaded = users.findById(saved.id()).orElseThrow();

        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.name()).isEqualTo(saved.name());
        assertThat(loaded.email()).isEqualTo(saved.email());
        assertThat(loaded.passwordHash()).isEqualTo(saved.passwordHash());
        assertThat(loaded.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    @DisplayName("timestamps survive the round trip at microsecond precision")
    void preservesMicrosecondTimestamps() {
        Instant withMicros = Instant.parse("2026-01-15T10:00:00Z").plusNanos(123_456_000);
        assertThat(withMicros.truncatedTo(ChronoUnit.MICROS)).isEqualTo(withMicros);

        User saved = users.save(ada(withMicros));

        assertThat(users.findById(saved.id()).orElseThrow().createdAt()).isEqualTo(withMicros);
    }

    @Test
    void findsByEmail() {
        User saved = users.save(ada(Instant.parse("2026-01-15T10:00:00Z")));

        assertThat(users.findByEmail(Email.of("ada@example.com")).orElseThrow().id()).isEqualTo(saved.id());
        assertThat(users.existsByEmail(Email.of("ada@example.com"))).isTrue();
    }

    @Test
    @DisplayName("an address stored by one casing is found by another, because both normalise")
    void emailLookupIsCaseInsensitiveThroughNormalisation() {
        users.save(ada(Instant.parse("2026-01-15T10:00:00Z")));

        assertThat(users.findByEmail(Email.of("ADA@Example.COM"))).isPresent();
    }

    @Test
    void reportsAbsence() {
        assertThat(users.findById(UserId.of(UUID.randomUUID()))).isEmpty();
        assertThat(users.findByEmail(Email.of("nobody@example.com"))).isEmpty();
        assertThat(users.existsByEmail(Email.of("nobody@example.com"))).isFalse();
    }

    @Test
    @DisplayName("the unique index surfaces as EmailAlreadyRegisteredException, not a raw 500")
    void translatesTheUniqueIndexViolation() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        users.save(ada(now));

        User clash = User.register(
                UserId.of(UUID.randomUUID()), FullName.of("Ada Byron"), Email.of("ada@example.com"), HASH, now);

        assertThatThrownBy(() -> users.save(clash)).isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    @DisplayName("the database rejects a duplicate even when the application check is bypassed entirely")
    void theUniqueIndexIsTheRealAuthority() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        users.save(ada(now));

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO users (id, name, email, password_hash, role, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        "Ada Byron",
                        "ada@example.com",
                        HASH.value(),
                        "STUDENT",
                        java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now)))
                .hasMessageContaining("users_email_key");
    }

    @Test
    @DisplayName("the CHECK constraint refuses an update timestamp that precedes creation")
    void enforcesTheTimestampOrderingInTheSchema() {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO users (id, name, email, password_hash, role, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        UUID.randomUUID(),
                        "Ada Byron",
                        "backwards@example.com",
                        HASH.value(),
                        "STUDENT",
                        java.sql.Timestamp.from(now),
                        java.sql.Timestamp.from(now.minusSeconds(1))))
                .hasMessageContaining("users_updated_at_not_before_created_at");
    }

    private User ada(Instant at) {
        return User.register(
                UserId.of(UUID.randomUUID()), FullName.of("Ada Lovelace"), Email.of("ada@example.com"), HASH, at);
    }
}
