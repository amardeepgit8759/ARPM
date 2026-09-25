package com.placefy.web.auth;

import com.placefy.application.port.in.UserProfile;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String role, Instant createdAt) {

    public static UserResponse from(UserProfile profile) {
        return new UserResponse(
                profile.id(), profile.name(), profile.email(), profile.role(), profile.createdAt());
    }
}
