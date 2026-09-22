package com.placefy.application.port.out;

/**
 * Produces the opaque secret handed to the client. This is the one place randomness enters the
 * auth flow, and it sits behind a port so every use-case test runs on a fixed sequence.
 */
public interface RefreshTokenGenerator {

    String generate();
}
