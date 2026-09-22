package com.placefy.domain.user;

import com.placefy.domain.DomainValidationException;
import java.time.Instant;
import java.util.Objects;

/**
 * A registered student.
 *
 * <p>Immutable, and it never reads a clock: every timestamp arrives as an argument. That is
 * what lets a use case be replayed with a fixed instant and produce byte-identical output,
 * which the scoring engine will depend on later and which costs nothing to establish now.
 */
public final class User {

    private final UserId id;
    private final FullName name;
    private final Email email;
    private final PasswordHash passwordHash;
    private final Role role;
    private final Instant createdAt;
    private final Instant updatedAt;

    private User(
            UserId id,
            FullName name,
            Email email,
            PasswordHash passwordHash,
            Role role,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new DomainValidationException("updatedAt", "updatedAt must not precede createdAt.");
        }
    }

    /** Creates a brand new account. The only role a registration can produce is STUDENT. */
    public static User register(
            UserId id, FullName name, Email email, PasswordHash passwordHash, Instant registeredAt) {
        return new User(id, name, email, passwordHash, Role.STUDENT, registeredAt, registeredAt);
    }

    /** Rebuilds a user already in storage. Used only by persistence mappers. */
    public static User rehydrate(
            UserId id,
            FullName name,
            Email email,
            PasswordHash passwordHash,
            Role role,
            Instant createdAt,
            Instant updatedAt) {
        return new User(id, name, email, passwordHash, role, createdAt, updatedAt);
    }

    public UserId id() {
        return id;
    }

    public FullName name() {
        return name;
    }

    public Email email() {
        return email;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof User that && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + ", role=" + role + "]";
    }
}
