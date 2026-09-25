package com.placefy.domain.roadmap;

/**
 * Why something could not be planned.
 *
 * <p>Every one of these is reported rather than absorbed. A plan that quietly shrank a topic to
 * make it fit, or dropped one without saying so, tells a student they are on track when they are
 * not — which is the failure this product exists to avoid.
 */
public enum DropReason {

    /** There was no room left in the horizon at the student's stated pace. */
    EXCEEDS_REMAINING_CAPACITY,

    /** A prerequisite did not fit, so this cannot be studied either. */
    PREREQUISITE_DROPPED
}
