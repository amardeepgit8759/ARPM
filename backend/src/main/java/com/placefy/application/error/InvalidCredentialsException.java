package com.placefy.application.error;

/**
 * Deliberately carries no detail about which half was wrong. The message is the same whether the
 * address is unknown or the password is incorrect.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email or password is incorrect.");
    }
}
