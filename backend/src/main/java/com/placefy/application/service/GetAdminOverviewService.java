package com.placefy.application.service;

import com.placefy.application.error.AdminAccessRequiredException;
import com.placefy.application.error.UserNotFoundException;
import com.placefy.application.port.in.GetAdminOverview;
import com.placefy.application.port.out.UserRepository;
import com.placefy.domain.user.Role;
import com.placefy.domain.user.User;
import com.placefy.domain.user.UserId;
import java.util.Objects;

/**
 * Authorisation lives here as well as in the security filter chain.
 *
 * <p>The filter reads the role claim from a token minted up to fifteen minutes ago. This reads the
 * role from storage, so an administrator demoted in the database loses access on their next
 * request, and a mistake in the URL rules cannot expose this on its own.
 */
public class GetAdminOverviewService implements GetAdminOverview {

    private final UserRepository users;

    public GetAdminOverviewService(UserRepository users) {
        this.users = Objects.requireNonNull(users);
    }

    @Override
    public AdminOverview handle(UserId requester) {
        User user = users.findById(requester).orElseThrow(() -> new UserNotFoundException(requester));
        if (!user.isAdmin()) {
            throw new AdminAccessRequiredException();
        }
        return new AdminOverview(users.countByRole(Role.STUDENT));
    }
}
