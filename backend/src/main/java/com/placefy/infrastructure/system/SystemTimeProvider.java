package com.placefy.infrastructure.system;

import com.placefy.application.port.out.TimeProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * The single place in the running application that reads a clock.
 *
 * <p>Truncated to microseconds because that is PostgreSQL's {@code timestamptz} resolution. An
 * untruncated nanosecond instant would differ from the value read back after a write, and every
 * equality assertion downstream would fail for a reason unrelated to the logic being tested.
 */
@Component
class SystemTimeProvider implements TimeProvider {

    private final Clock clock = Clock.systemUTC();

    @Override
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
