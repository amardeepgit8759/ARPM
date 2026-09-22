package com.placefy.application.port.out;

import com.placefy.domain.user.User;
import java.time.Instant;

public interface AccessTokenIssuer {

    /** Mints a signed access token. The instant is supplied, never read from a clock here. */
    IssuedAccessToken issue(User user, Instant issuedAt);

    record IssuedAccessToken(String value, Instant expiresAt) {}
}
