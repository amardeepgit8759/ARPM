package com.placefy.web.user;

import com.placefy.application.port.in.GetCurrentUser;
import com.placefy.application.port.in.UserProfile;
import com.placefy.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final GetCurrentUser getCurrentUser;

    MeController(GetCurrentUser getCurrentUser) {
        this.getCurrentUser = getCurrentUser;
    }

    /**
     * The identity comes from the verified token's subject and from nowhere else. There is no path
     * variable and no query parameter to substitute, so "read another user" is not an operation
     * this API can express.
     */
    @GetMapping
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        UserProfile profile = getCurrentUser.handle(UserId.parse(jwt.getSubject()));
        return new MeResponse(profile.id(), profile.name(), profile.email(), profile.role(), profile.createdAt());
    }

    record MeResponse(UUID id, String name, String email, String role, Instant createdAt) {}
}
