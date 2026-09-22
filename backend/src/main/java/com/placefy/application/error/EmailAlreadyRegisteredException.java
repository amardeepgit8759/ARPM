package com.placefy.application.error;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with that email already exists.");
    }
}
