package com.placefy.domain.roadmap;

import com.placefy.domain.DomainValidationException;
import java.util.List;
import java.util.Objects;

/**
 * Every tunable the planner obeys, supplied by the caller.
 *
 * <p>No value here has a default. They are pacing rules, and CLAUDE.md rule 8 puts pacing rules
 * in versioned configuration rather than in Java — a planner that quietly assumed "90 minutes a
 * day" would be making a product decision inside an algorithm.
 *
 * @param maxNewSubSkillsPerDay applies to new study only. Revision sessions are additional and
 *     bounded by the minute cap instead, because the limit exists to cap how much unfamiliar
 *     material a person meets in a day, and revisiting something already studied is not that.
 * @param revisionOffsetDays days after a sub-skill's final study session at which to revise it.
 *     Must be strictly increasing so the spacing is genuinely expanding.
 */
public record PlanningConfig(
        int weeklyHours,
        int maxMinutesPerDay,
        int maxNewSubSkillsPerDay,
        List<Integer> revisionOffsetDays,
        int revisionSessionMinutes,
        int checkpointCadenceDays,
        int checkpointMinutes) {

    public PlanningConfig {
        requirePositive("weeklyHours", weeklyHours);
        requirePositive("maxMinutesPerDay", maxMinutesPerDay);
        requirePositive("maxNewSubSkillsPerDay", maxNewSubSkillsPerDay);
        requirePositive("revisionSessionMinutes", revisionSessionMinutes);

        if (checkpointCadenceDays < 0) {
            throw new DomainValidationException(
                    "checkpointCadenceDays", "Checkpoint cadence cannot be negative; use 0 to disable checkpoints.");
        }
        if (checkpointMinutes < 0) {
            throw new DomainValidationException("checkpointMinutes", "Checkpoint minutes cannot be negative.");
        }

        revisionOffsetDays = revisionOffsetDays == null ? List.of() : List.copyOf(revisionOffsetDays);
        int previous = 0;
        for (Integer offset : revisionOffsetDays) {
            Objects.requireNonNull(offset, "a revision offset must not be null");
            if (offset <= previous) {
                throw new DomainValidationException(
                        "revisionOffsetDays",
                        "Revision offsets must be positive and strictly increasing, got " + revisionOffsetDays + ".");
            }
            previous = offset;
        }

        if (revisionSessionMinutes > maxMinutesPerDay) {
            throw new DomainValidationException(
                    "revisionSessionMinutes",
                    "A revision session of " + revisionSessionMinutes
                            + " minutes can never fit inside a daily cap of " + maxMinutesPerDay + ".");
        }
        if (checkpointMinutes > maxMinutesPerDay) {
            throw new DomainValidationException(
                    "checkpointMinutes",
                    "A checkpoint of " + checkpointMinutes + " minutes can never fit inside a daily cap of "
                            + maxMinutesPerDay + ".");
        }
    }

    public int weeklyMinutes() {
        return weeklyHours * 60;
    }

    public boolean checkpointsEnabled() {
        return checkpointCadenceDays > 0;
    }

    private static void requirePositive(String field, int value) {
        if (value < 1) {
            throw new DomainValidationException(field, field + " must be at least 1, got " + value + ".");
        }
    }
}
