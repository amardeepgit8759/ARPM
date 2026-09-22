package com.placefy.application.port.out;

import java.util.UUID;

/**
 * The only way any use case obtains a new identifier, so tests can pin the ids a run produces
 * and assert on exact output rather than "some UUID".
 */
public interface IdGenerator {

    UUID newId();
}
