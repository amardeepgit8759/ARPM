package com.placefy.domain.narrative;

/**
 * How badly a gap matters against the target role's hiring bar.
 *
 * <p>The three levels are the ones the requirement model will carry. They are declared here
 * because the decision trace is currently their only consumer; when the company requirement
 * slice lands it should own this enum and this one should be deleted rather than duplicated.
 */
public enum GapCriticality {
    BLOCKING,
    IMPORTANT,
    NICE_TO_HAVE
}
