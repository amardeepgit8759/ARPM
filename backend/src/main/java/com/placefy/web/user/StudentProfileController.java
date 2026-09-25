package com.placefy.web.user;

import com.placefy.application.port.in.GetCurrentUser;
import com.placefy.application.port.in.UpdateMyProfile;
import com.placefy.application.port.in.UserProfile;
import com.placefy.domain.user.UserId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own profile. Served to administrators as well — "students" names the product's
 * subject, and an admin still needs to see and edit their own name.
 */
@RestController
@RequestMapping("/api/v1/students/me")
class StudentProfileController {

    private final GetCurrentUser getCurrentUser;
    private final UpdateMyProfile updateMyProfile;

    StudentProfileController(GetCurrentUser getCurrentUser, UpdateMyProfile updateMyProfile) {
        this.getCurrentUser = getCurrentUser;
        this.updateMyProfile = updateMyProfile;
    }

    /**
     * The identity comes from the verified token's subject and from nowhere else. There is no path
     * variable and no query parameter to substitute, so "read another user" is not an operation
     * this API can express.
     */
    @GetMapping
    ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return ProfileResponse.from(getCurrentUser.handle(UserId.parse(jwt.getSubject())));
    }

    /** Full replacement of the editable fields, which today is the name alone. */
    @PutMapping
    ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateProfileRequest request) {
        return ProfileResponse.from(updateMyProfile.handle(
                new UpdateMyProfile.UpdateMyProfileCommand(UserId.parse(jwt.getSubject()), request.name())));
    }

    record UpdateProfileRequest(String name) {}

    record ProfileResponse(UUID id, String name, String email, String role, Instant createdAt) {

        static ProfileResponse from(UserProfile profile) {
            return new ProfileResponse(
                    profile.id(), profile.name(), profile.email(), profile.role(), profile.createdAt());
        }
    }
}
