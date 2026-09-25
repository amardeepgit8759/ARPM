package com.placefy.application.port.in;

import com.placefy.domain.user.UserId;

/**
 * Changes what a user may change about themselves: their display name.
 *
 * <p>Email is not editable here. Changing it would change the login identifier, which needs a
 * verification step this product does not yet have; accepting it silently would let a stolen
 * session take over the account.
 */
public interface UpdateMyProfile {

    UserProfile handle(UpdateMyProfileCommand command);

    record UpdateMyProfileCommand(UserId userId, String name) {}
}
