package com.placefy.domain.roadmap;

import com.placefy.domain.DomainValidationException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * The window a plan is allowed to occupy.
 *
 * <p>The start date arrives as an argument. The planner never reads a clock, so replanning the
 * same request tomorrow with the same start date produces the identical plan — which is what
 * lets a superseded roadmap be compared against its replacement and the difference attributed to
 * changed evidence rather than to the passage of time.
 *
 * @param finalRevisionDays days at the end reserved for revision and checkpoints only. No new
 *     study is scheduled there. Without it a plan packs new material into the last day before an
 *     interview, which is the one time nobody should be meeting a topic for the first time.
 */
public record PlanningHorizon(LocalDate startDate, int weeks, int finalRevisionDays) {

    public PlanningHorizon {
        Objects.requireNonNull(startDate, "startDate");
        if (weeks < 1) {
            throw new DomainValidationException("weeks", "A plan needs at least one week, got " + weeks + ".");
        }
        if (finalRevisionDays < 0) {
            throw new DomainValidationException(
                    "finalRevisionDays", "Reserved revision days cannot be negative, got " + finalRevisionDays + ".");
        }
        if (finalRevisionDays >= weeks * 7) {
            throw new DomainValidationException(
                    "finalRevisionDays",
                    "Reserving " + finalRevisionDays + " days leaves no room to study in a "
                            + (weeks * 7) + "-day horizon.");
        }
    }

    public static PlanningHorizon of(LocalDate startDate, int weeks, int finalRevisionDays) {
        return new PlanningHorizon(startDate, weeks, finalRevisionDays);
    }

    public int totalDays() {
        return weeks * 7;
    }

    /** Inclusive. */
    public LocalDate endDate() {
        return startDate.plusDays(totalDays() - 1L);
    }

    /** The last day new study may be scheduled. Inclusive. */
    public LocalDate lastStudyDate() {
        return endDate().minusDays(finalRevisionDays);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate());
    }

    public boolean isReservedForRevision(LocalDate date) {
        return date.isAfter(lastStudyDate()) && !date.isAfter(endDate());
    }

    /** Zero-based, so a weekly budget can be tracked without calendar week arithmetic. */
    public int weekIndexOf(LocalDate date) {
        return (int) (ChronoUnit.DAYS.between(startDate, date) / 7);
    }
}
