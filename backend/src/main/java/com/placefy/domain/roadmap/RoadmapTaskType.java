package com.placefy.domain.roadmap;

/** What a scheduled block of time is for. */
public enum RoadmapTaskType {

    /** New material. Bounded by the daily minute cap and the new-sub-skills-per-day cap. */
    STUDY,

    /** Revisiting something already studied, at one of the configured spacing offsets. */
    REVISION,

    /** An assessment checkpoint, placed by cadence rather than by content. */
    CHECKPOINT
}
