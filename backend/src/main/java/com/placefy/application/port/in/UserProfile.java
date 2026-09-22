package com.placefy.application.port.in;

import com.placefy.domain.user.User;
import java.time.Instant;
import java.util.UUID;

/**
 * What the outside world is allowed to know about a user. Notably absent: the password hash.
 * Building this from the aggregate rather than serialising the aggregate is what keeps that
 * guarantee from depending on someone remembering to annotate a field.
 */
public record UserProfile(UUID id, String name, String email, String role, Instant createdAt) {

    public static UserProfile from(User user) {
        return new UserProfile(
                user.id().value(),
                user.name().value(),
                user.email().value(),
                user.role().name(),
                user.createdAt());
    }
}
