package com.placefy.application.fake;

import com.placefy.application.port.out.TimeProvider;
import java.time.Duration;
import java.time.Instant;

public class FixedTimeProvider implements TimeProvider {

    private Instant now;

    public FixedTimeProvider(Instant now) {
        this.now = now;
    }

    @Override
    public Instant now() {
        return now;
    }

    public void advanceBy(Duration duration) {
        now = now.plus(duration);
    }
}
