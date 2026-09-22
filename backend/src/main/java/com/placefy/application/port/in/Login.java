package com.placefy.application.port.in;

public interface Login {

    AuthenticatedSession handle(LoginCommand command);

    record LoginCommand(String email, String password) {}
}
