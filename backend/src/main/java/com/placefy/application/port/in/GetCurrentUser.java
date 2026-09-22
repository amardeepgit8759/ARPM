package com.placefy.application.port.in;

import com.placefy.domain.user.UserId;

/**
 * Reads the profile of the authenticated user.
 *
 * <p>The id is a parameter rather than something the use case looks up, and the only caller
 * passes the subject of the verified JWT. There is deliberately no variant that takes an id from
 * a request body or path: a user can only ever read themselves.
 */
public interface GetCurrentUser {

    UserProfile handle(UserId userId);
}
