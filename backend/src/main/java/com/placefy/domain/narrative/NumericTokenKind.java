package com.placefy.domain.narrative;

/** How a number was written. Recorded for diagnostics; every kind is validated identically. */
enum NumericTokenKind {
    INTEGER,
    DECIMAL,
    PERCENTAGE,
    GROUPED,
    ORDINAL,
    MULTIPLIER,
    NEGATIVE,
    SPELLED
}
