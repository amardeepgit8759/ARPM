package com.placefy.application.service;

import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.UpdateMyProfile;
import com.placefy.application.port.in.UserProfile;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.FullName;
import com.placefy.domain.user.User;
import java.util.Objects;

public class UpdateMyProfileService implements UpdateMyProfile {

    private final UserRepository users;
    private final TimeProvider time;

    public UpdateMyProfileService(UserRepository users, TimeProvider time) {
        this.users = Objects.requireNonNull(users);
        this.time = Objects.requireNonNull(time);
    }

    @Override
    public UserProfile handle(UpdateMyProfileCommand command) {
        // Validated before loading, so a bad name is a 400 whether or not the account exists.
        FullName name = FullName.of(command.name());
        User user = users.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));

        return UserProfile.from(users.save(user.rename(name, time.now())));
    }
}
