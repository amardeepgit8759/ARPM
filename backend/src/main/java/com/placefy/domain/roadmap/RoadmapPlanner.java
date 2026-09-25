package com.placefy.domain.roadmap;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a prioritised set of sub-skills into a dated plan.
 *
 * <p>Pure and deterministic. No clock, no randomness, no I/O — the same request always produces
 * the identical roadmap, which is what makes a "your plan changed because…" diff attributable to
 * changed evidence rather than to when the button was pressed.
 *
 * <p>The order of work is deliberate. Checkpoints are placed first because their dates are fixed
 * by cadence and study must work around them. Study comes next, in prerequisite order. Revision
 * comes last, because a revisit can only be dated once its subject has a final session — and
 * because when capacity runs out it is better to lose a revisit than a first encounter.
 *
 * <p>Capacity is never stretched and effort is never shrunk. When a sub-skill will not fit, it is
 * dropped whole and named, along with everything downstream of it.
 */
public final class RoadmapPlanner {

    public Roadmap plan(PlanningRequest request) {
        PlanningHorizon horizon = request.horizon();
        PlanningConfig config = request.config();

        Map<LocalDate, List<RoadmapTask>> schedule = emptyHorizon(horizon);
        Map<Integer, Integer> weeklyMinutesUsed = new LinkedHashMap<>();

        placeCheckpoints(horizon, config, schedule, weeklyMinutesUsed);

        List<PlannedSubSkill> ordered = PrerequisiteOrdering.order(request.targets());
        StudyOutcome study = placeStudy(ordered, horizon, config, schedule, weeklyMinutesUsed);
        List<UnscheduledRevision> unscheduled =
                placeRevision(study, horizon, config, schedule, weeklyMinutesUsed);

        return new Roadmap(horizon, config, materialise(horizon, schedule), study.dropped(), unscheduled);
    }

    private static Map<LocalDate, List<RoadmapTask>> emptyHorizon(PlanningHorizon horizon) {
        Map<LocalDate, List<RoadmapTask>> schedule = new LinkedHashMap<>();
        for (int offset = 0; offset < horizon.totalDays(); offset++) {
            schedule.put(horizon.startDate().plusDays(offset), new ArrayList<>());
        }
        return schedule;
    }

    private static void placeCheckpoints(
            PlanningHorizon horizon,
            PlanningConfig config,
            Map<LocalDate, List<RoadmapTask>> schedule,
            Map<Integer, Integer> weeklyMinutesUsed) {

        if (!config.checkpointsEnabled() || config.checkpointMinutes() == 0) {
            return;
        }

        for (int offset = config.checkpointCadenceDays(); offset < horizon.totalDays();
                offset += config.checkpointCadenceDays()) {
            LocalDate date = horizon.startDate().plusDays(offset);
            schedule.get(date).add(RoadmapTask.checkpoint(config.checkpointMinutes()));
            consume(horizon, weeklyMinutesUsed, date, config.checkpointMinutes());
        }
    }

    /** What study placement produced, and what it had to give up. */
    private record StudyOutcome(Map<SubSkillCode, StudiedSubSkill> studied, List<DroppedSubSkill> dropped) {}

    private record StudiedSubSkill(PlannedSubSkill subSkill, LocalDate lastSessionDate) {}

    private static StudyOutcome placeStudy(
            List<PlannedSubSkill> ordered,
            PlanningHorizon horizon,
            PlanningConfig config,
            Map<LocalDate, List<RoadmapTask>> schedule,
            Map<Integer, Integer> weeklyMinutesUsed) {

        Map<SubSkillCode, StudiedSubSkill> studied = new LinkedHashMap<>();
        List<DroppedSubSkill> dropped = new ArrayList<>();
        Set<SubSkillCode> droppedCodes = new HashSet<>();

        for (PlannedSubSkill target : ordered) {
            SubSkillCode blocker = droppedPrerequisiteOf(target, droppedCodes);
            if (blocker != null) {
                dropped.add(new DroppedSubSkill(
                        target.code(),
                        target.name(),
                        DropReason.PREREQUISITE_DROPPED,
                        "Requires '" + blocker.value() + "', which did not fit."));
                droppedCodes.add(target.code());
                continue;
            }

            LocalDate earliest = earliestStart(target, studied, horizon);
            List<Session> sessions =
                    trySchedule(target, earliest, horizon, config, schedule, weeklyMinutesUsed);

            if (sessions.isEmpty()) {
                dropped.add(new DroppedSubSkill(
                        target.code(),
                        target.name(),
                        DropReason.EXCEEDS_REMAINING_CAPACITY,
                        target.effortMinutes() + " minutes would not fit before "
                                + horizon.lastStudyDate() + " at this pace."));
                droppedCodes.add(target.code());
                continue;
            }

            commit(target, sessions, schedule, horizon, weeklyMinutesUsed);
            studied.put(target.code(), new StudiedSubSkill(target, sessions.get(sessions.size() - 1).date()));
        }

        return new StudyOutcome(studied, List.copyOf(dropped));
    }

    private static SubSkillCode droppedPrerequisiteOf(PlannedSubSkill target, Set<SubSkillCode> droppedCodes) {
        return target.prerequisites().stream().filter(droppedCodes::contains).findFirst().orElse(null);
    }

    /**
     * A sub-skill may begin on the same day its last prerequisite finishes — tasks within a day
     * are ordered, and study is appended in prerequisite order, so the prerequisite still comes
     * first on the page. Forcing a fresh day would waste capacity for no pedagogical gain.
     */
    private static LocalDate earliestStart(
            PlannedSubSkill target, Map<SubSkillCode, StudiedSubSkill> studied, PlanningHorizon horizon) {

        LocalDate earliest = horizon.startDate();
        for (SubSkillCode prerequisite : target.prerequisites()) {
            StudiedSubSkill done = studied.get(prerequisite);
            if (done != null && done.lastSessionDate().isAfter(earliest)) {
                earliest = done.lastSessionDate();
            }
        }
        return earliest;
    }

    private record Session(LocalDate date, int minutes) {}

    /**
     * Walks forward looking for room, without writing anything. Returning an empty list means the
     * whole sub-skill does not fit — and because nothing was written, no partial study is left
     * behind for a topic that will never be finished.
     */
    private static List<Session> trySchedule(
            PlannedSubSkill target,
            LocalDate from,
            PlanningHorizon horizon,
            PlanningConfig config,
            Map<LocalDate, List<RoadmapTask>> schedule,
            Map<Integer, Integer> weeklyMinutesUsed) {

        List<Session> sessions = new ArrayList<>();
        Map<Integer, Integer> provisionalWeekly = new LinkedHashMap<>(weeklyMinutesUsed);
        int remaining = target.effortMinutes();
        LocalDate date = from;

        while (remaining > 0) {
            if (date.isAfter(horizon.lastStudyDate())) {
                return List.of();
            }

            int available = availableMinutes(date, horizon, config, schedule, provisionalWeekly, sessions);
            if (available > 0 && hasRoomForAnotherSubSkill(target, date, config, schedule, sessions)) {
                int chunk = Math.min(remaining, available);
                sessions.add(new Session(date, chunk));
                provisionalWeekly.merge(horizon.weekIndexOf(date), chunk, Integer::sum);
                remaining -= chunk;
            }

            if (remaining > 0) {
                date = date.plusDays(1);
            }
        }

        return sessions;
    }

    private static int availableMinutes(
            LocalDate date,
            PlanningHorizon horizon,
            PlanningConfig config,
            Map<LocalDate, List<RoadmapTask>> schedule,
            Map<Integer, Integer> weeklyUsed,
            List<Session> provisional) {

        int committedToday = schedule.get(date).stream().mapToInt(RoadmapTask::minutes).sum();
        int provisionalToday =
                provisional.stream().filter(s -> s.date().equals(date)).mapToInt(Session::minutes).sum();

        int dayRoom = config.maxMinutesPerDay() - committedToday - provisionalToday;
        int weekRoom = config.weeklyMinutes() - weeklyUsed.getOrDefault(horizon.weekIndexOf(date), 0);

        return Math.max(0, Math.min(dayRoom, weekRoom));
    }

    private static boolean hasRoomForAnotherSubSkill(
            PlannedSubSkill target,
            LocalDate date,
            PlanningConfig config,
            Map<LocalDate, List<RoadmapTask>> schedule,
            List<Session> provisional) {

        Set<SubSkillCode> onThisDay = new HashSet<>();
        schedule.get(date).stream()
                .filter(RoadmapTask::isStudy)
                .forEach(task -> onThisDay.add(task.subSkillCode()));

        if (provisional.stream().anyMatch(session -> session.date().equals(date))) {
            onThisDay.add(target.code());
        }

        return onThisDay.contains(target.code()) || onThisDay.size() < config.maxNewSubSkillsPerDay();
    }

    private static void commit(
            PlannedSubSkill target,
            List<Session> sessions,
            Map<LocalDate, List<RoadmapTask>> schedule,
            PlanningHorizon horizon,
            Map<Integer, Integer> weeklyMinutesUsed) {

        for (int i = 0; i < sessions.size(); i++) {
            Session session = sessions.get(i);
            schedule.get(session.date())
                    .add(RoadmapTask.study(
                            target.code(), target.name(), session.minutes(), i + 1, sessions.size()));
            consume(horizon, weeklyMinutesUsed, session.date(), session.minutes());
        }
    }

    /**
     * Spaced revision. Each offset is measured from the sub-skill's final study session, and when
     * the exact day is full the session slides to the next day that has room — spacing that is
     * approximately right beats a revisit that silently disappears.
     */
    private static List<UnscheduledRevision> placeRevision(
            StudyOutcome study,
            PlanningHorizon horizon,
            PlanningConfig config,
            Map<LocalDate, List<RoadmapTask>> schedule,
            Map<Integer, Integer> weeklyMinutesUsed) {

        List<UnscheduledRevision> unscheduled = new ArrayList<>();
        List<Integer> offsets = config.revisionOffsetDays();

        for (StudiedSubSkill studied : study.studied().values()) {
            for (int i = 0; i < offsets.size(); i++) {
                int offset = offsets.get(i);
                LocalDate intended = studied.lastSessionDate().plusDays(offset);
                LocalDate placed = findRevisionSlot(intended, horizon, config, schedule, weeklyMinutesUsed);

                if (placed == null) {
                    unscheduled.add(new UnscheduledRevision(
                            studied.subSkill().code(),
                            studied.subSkill().name(),
                            offset,
                            intended,
                            intended.isAfter(horizon.endDate())
                                    ? "The +" + offset + " day review falls after the end of the plan."
                                    : "No day between " + intended + " and " + horizon.endDate()
                                            + " had room left."));
                    continue;
                }

                schedule.get(placed)
                        .add(RoadmapTask.revision(
                                studied.subSkill().code(),
                                studied.subSkill().name(),
                                config.revisionSessionMinutes(),
                                i + 1,
                                offsets.size()));
                consume(horizon, weeklyMinutesUsed, placed, config.revisionSessionMinutes());
            }
        }

        return List.copyOf(unscheduled);
    }

    private static LocalDate findRevisionSlot(
            LocalDate intended,
            PlanningHorizon horizon,
            PlanningConfig config,
            Map<LocalDate, List<RoadmapTask>> schedule,
            Map<Integer, Integer> weeklyMinutesUsed) {

        if (intended.isAfter(horizon.endDate())) {
            return null;
        }

        LocalDate date = intended.isBefore(horizon.startDate()) ? horizon.startDate() : intended;
        while (!date.isAfter(horizon.endDate())) {
            int committed = schedule.get(date).stream().mapToInt(RoadmapTask::minutes).sum();
            int dayRoom = config.maxMinutesPerDay() - committed;
            int weekRoom = config.weeklyMinutes() - weeklyMinutesUsed.getOrDefault(horizon.weekIndexOf(date), 0);

            if (Math.min(dayRoom, weekRoom) >= config.revisionSessionMinutes()) {
                return date;
            }
            date = date.plusDays(1);
        }
        return null;
    }

    private static void consume(
            PlanningHorizon horizon, Map<Integer, Integer> weeklyMinutesUsed, LocalDate date, int minutes) {
        weeklyMinutesUsed.merge(horizon.weekIndexOf(date), minutes, Integer::sum);
    }

    private static List<RoadmapDay> materialise(
            PlanningHorizon horizon, Map<LocalDate, List<RoadmapTask>> schedule) {
        List<RoadmapDay> days = new ArrayList<>();
        for (int offset = 0; offset < horizon.totalDays(); offset++) {
            LocalDate date = horizon.startDate().plusDays(offset);
            days.add(new RoadmapDay(date, List.copyOf(schedule.get(date))));
        }
        return List.copyOf(days);
    }
}
