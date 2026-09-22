package com.placefy.application.port.out;

import java.time.Instant;

/**
 * The only way any use case learns the current time.
 *
 * <p>Implementations must return UTC instants truncated to microseconds. PostgreSQL's
 * {@code timestamptz} keeps microseconds, so an untruncated nanosecond instant would not survive
 * a write-then-read round trip and equality assertions would fail for reasons that have nothing
 * to do with the logic under test.
 */
public interface TimeProvider {

    Instant now();
}
