package com.placefy.application.port.in;

public interface RegisterUser {

    AuthenticatedSession handle(RegisterUserCommand command);

    /** Raw strings: validating them into domain value objects is the use case's first job. */
    record RegisterUserCommand(String name, String email, String password) {}
}
