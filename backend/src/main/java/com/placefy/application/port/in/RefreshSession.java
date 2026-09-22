package com.placefy.application.port.in;

public interface RefreshSession {

    AuthenticatedSession handle(RefreshSessionCommand command);

    record RefreshSessionCommand(String presentedRefreshToken) {}
}
