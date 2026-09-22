package com.placefy.application.fake;

import com.placefy.application.port.out.IdGenerator;
import java.util.UUID;

/**
 * Hands out 0000...0001, 0000...0002 and so on, so a test can assert the exact identifiers a run
 * produced instead of settling for "some UUID".
 */
public class SequentialIdGenerator implements IdGenerator {

    private long next = 1;

    @Override
    public UUID newId() {
        return new UUID(0L, next++);
    }

    public static UUID nth(long n) {
        return new UUID(0L, n);
    }
}
