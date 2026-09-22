package com.placefy.web.auth;

/**
 * Inbound request bodies.
 *
 * <p>No bean-validation annotations. The domain value objects already reject a blank name, a
 * malformed address and a short password, with a field name attached; repeating those rules here
 * would create a second place to change them and a way for the two to disagree.
 */
final class AuthRequests {

    private AuthRequests() {}

    record RegisterRequest(String name, String email, String password) {}

    record LoginRequest(String email, String password) {}
}
