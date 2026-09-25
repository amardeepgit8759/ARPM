package com.placefy.domain.roadmap;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A spaced-revision session that could not be placed.
 *
 * <p>Distinct from a dropped sub-skill: the topic was studied, only one of its revisits did not
 * fit — usually because the offset lands past the end of the horizon. Recorded so the plan can
 * say "your +24 day review falls after your target date" rather than simply omitting it.
 *
 * @param intendedDate where the offset pointed before capacity was considered
 */
public record UnscheduledRevision(
        SubSkillCode code, String name, int offsetDays, LocalDate intendedDate, String reason) {

    public UnscheduledRevision {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(intendedDate, "intendedDate");
        name = name == null ? "" : name;
        reason = reason == null ? "" : reason;
    }
}
