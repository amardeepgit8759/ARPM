package com.placefy.application.service;

import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.GetCurrentUser;
import com.placefy.application.port.in.UserProfile;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.UserId;
import java.util.Objects;

public class GetCurrentUserService implements GetCurrentUser {

    private final UserRepository users;

    public GetCurrentUserService(UserRepository users) {
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public UserProfile handle(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        return users.findById(userId)
                .map(UserProfile::from)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
