package com.placefy.application.error;

/**
 * The caller is authenticated but is not an administrator.
 *
 * <p>Checked against the stored role, not only the token's claim, so revoking ADMIN in the
 * database takes effect on the next request rather than when the access token expires.
 */
public class AdminAccessRequiredException extends RuntimeException {

    public AdminAccessRequiredException() {
        super("This resource is available to administrators only.");
    }
}
