package com.placefy.application.service;

import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.ExportMyData;
import com.placefy.application.port.in.UserDataExport;
import com.placefy.application.port.out.RefreshTokenRepository;
import com.placefy.application.port.out.TimeProvider;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import java.util.Objects;

public class ExportMyDataService implements ExportMyData {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TimeProvider time;

    public ExportMyDataService(UserRepository users, RefreshTokenRepository refreshTokens, TimeProvider time) {
        this.users = Objects.requireNonNull(users);
        this.refreshTokens = Objects.requireNonNull(refreshTokens);
        this.time = Objects.requireNonNull(time);
    }

    @Override
    public UserDataExport handle(UserId userId) {
        Objects.requireNonNull(userId, "userId");

        User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        // Scoped by the id from the token, in the use case, as every user-owned read must be.
        return UserDataExport.of(user, refreshTokens.findByUser(userId), time.now());
    }
}
