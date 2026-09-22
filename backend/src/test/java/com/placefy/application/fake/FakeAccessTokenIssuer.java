package com.placefy.application.fake;

import com.placefy.application.port.out.AccessTokenIssuer;
import com.placefy.domain.user.User;
import java.time.Duration;
import java.time.Instant;

public class FakeAccessTokenIssuer implements AccessTokenIssuer {

    public static final Duration TTL = Duration.ofMinutes(15);

    @Override
    public IssuedAccessToken issue(User user, Instant issuedAt) {
        return new IssuedAccessToken("access-token-for-" + user.id() + "-at-" + issuedAt, issuedAt.plus(TTL));
    }
}
