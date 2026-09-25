package com.placefy.domain.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * The invariants that must hold for every plan the engine can produce.
 *
 * <p>Generated rather than enumerated because the interesting failures are shapes nobody writes
 * by hand: a horizon just one day too short, a topic exactly the size of the daily cap, a chain
 * of prerequisites eight deep, a week whose budget runs out mid-topic.
 *
 * <p>Requests are generated with backward-only prerequisite references, so the graph is acyclic
 * by construction and the planner is being tested on scheduling rather than on rejecting input
 * it would never receive from a validated taxonomy.
 */
class RoadmapPlannerProperties {

    private final RoadmapPlanner planner = new RoadmapPlanner();

    @Property
    void neverExceedsTheDailyMinuteCap(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);

        assertThat(roadmap.days())
                .allSatisfy(day -> assertThat(day.totalMinutes())
                        .as("minutes on %s", day.date())
                        .isLessThanOrEqualTo(request.config().maxMinutesPerDay()));
    }

    @Property
    void neverExceedsTheNewSubSkillsPerDayCap(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);

        assertThat(roadmap.days())
                .allSatisfy(day -> assertThat(day.newStudySubSkills().size())
                        .as("new sub-skills on %s", day.date())
                        .isLessThanOrEqualTo(request.config().maxNewSubSkillsPerDay()));
    }

    @Property
    void neverSchedulesADependentBeforeItsPrerequisite(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);
        Map<SubSkillCode, LocalDate> firstStudy = new HashMap<>();
        Map<SubSkillCode, LocalDate> lastStudy = new HashMap<>();

        for (RoadmapDay day : roadmap.days()) {
            for (RoadmapTask task : day.tasks()) {
                if (task.isStudy()) {
                    firstStudy.putIfAbsent(task.subSkillCode(), day.date());
                    lastStudy.put(task.subSkillCode(), day.date());
                }
            }
        }

        for (PlannedSubSkill target : request.targets()) {
            LocalDate start = firstStudy.get(target.code());
            if (start == null) {
                continue;
            }
            for (SubSkillCode prerequisite : target.prerequisites()) {
                LocalDate prerequisiteEnd = lastStudy.get(prerequisite);
                if (prerequisiteEnd != null) {
                    assertThat(start)
                            .as("%s starts before its prerequisite %s finishes", target.code(), prerequisite)
                            .isAfterOrEqualTo(prerequisiteEnd);
                }
            }
        }
    }

    @Property
    void neverExceedsTheHorizon(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);
        PlanningHorizon horizon = request.horizon();

        assertThat(roadmap.days()).hasSize(horizon.totalDays());
        assertThat(roadmap.days())
                .allSatisfy(day -> assertThat(horizon.contains(day.date()))
                        .as("%s falls outside the horizon", day.date())
                        .isTrue());
    }

    @Property
    void neverSchedulesNewStudyInTheReservedFinalWindow(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);

        roadmap.days().stream()
                .filter(day -> request.horizon().isReservedForRevision(day.date()))
                .forEach(day -> assertThat(day.tasks())
                        .as("new study on reserved day %s", day.date())
                        .noneMatch(RoadmapTask::isStudy));
    }

    @Property
    void producesTheSamePlanForTheSameRequest(@ForAll("requests") PlanningRequest request) {
        assertThat(planner.plan(request)).isEqualTo(planner.plan(request));
    }

    /**
     * The "never silent compression" guarantee: a topic is either scheduled for every minute of
     * its effort, or it appears in the drop list. There is no middle state where a student is
     * shown a shortened version of a topic and told they have covered it.
     */
    @Property
    void everySubSkillIsEitherFullyScheduledOrExplicitlyDropped(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);

        Set<SubSkillCode> dropped = new HashSet<>();
        roadmap.droppedSubSkills().forEach(drop -> dropped.add(drop.code()));
        Set<SubSkillCode> scheduled = new HashSet<>(roadmap.scheduledSubSkills());

        for (PlannedSubSkill target : request.targets()) {
            assertThat(scheduled.contains(target.code()) ^ dropped.contains(target.code()))
                    .as("%s must be exactly one of scheduled or dropped", target.code())
                    .isTrue();

            if (scheduled.contains(target.code())) {
                assertThat(roadmap.studyMinutesFor(target.code()))
                        .as("planned minutes for %s", target.code())
                        .isEqualTo(target.effortMinutes());
            }
        }
    }

    @Property
    void neverInventsASubSkillNobodyAskedFor(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);
        Set<SubSkillCode> requested = new HashSet<>();
        request.targets().forEach(target -> requested.add(target.code()));

        roadmap.days().stream()
                .flatMap(day -> day.tasks().stream())
                .forEach(task -> task.subSkill()
                        .ifPresent(code -> assertThat(requested)
                                .as("task for unrequested sub-skill %s", code)
                                .contains(code)));
    }

    @Property
    void revisionOnlyEverFollowsStudyOfTheSameSubSkill(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);
        Map<SubSkillCode, LocalDate> lastStudy = new HashMap<>();

        for (RoadmapDay day : roadmap.days()) {
            for (RoadmapTask task : day.tasks()) {
                if (task.isStudy()) {
                    lastStudy.put(task.subSkillCode(), day.date());
                } else if (task.type() == RoadmapTaskType.REVISION) {
                    assertThat(lastStudy.get(task.subSkillCode()))
                            .as("revision of %s on %s with no prior study", task.subSkillCode(), day.date())
                            .isNotNull()
                            .isBeforeOrEqualTo(day.date());
                }
            }
        }
    }

    /**
     * Guards against the suite passing vacuously.
     *
     * <p>Every invariant above is trivially true of an empty plan, so without this a generator
     * that quietly produced nothing but empty roadmaps would report a clean run while testing
     * none of the behaviour that matters.
     */
    @Property(tries = 500)
    void theGeneratorReachesTheCasesWorthTesting(@ForAll("requests") PlanningRequest request) {
        Roadmap roadmap = planner.plan(request);

        Statistics.label("outcome")
                .collect(roadmap.scheduledSubSkills().isEmpty() && roadmap.droppedSubSkills().isEmpty()
                        ? "nothing planned"
                        : roadmap.droppedSubSkills().isEmpty() ? "all scheduled" : "some dropped");

        boolean anySplit = roadmap.scheduledSubSkills().stream()
                .anyMatch(code -> roadmap.days().stream()
                                .flatMap(day -> day.tasks().stream())
                                .filter(RoadmapTask::isStudy)
                                .filter(task -> code.equals(task.subSkillCode()))
                                .count()
                        > 1);
        Statistics.label("multi-session topics").collect(anySplit ? "present" : "absent");

        boolean anyPrerequisite = request.targets().stream().anyMatch(t -> !t.prerequisites().isEmpty());
        Statistics.label("prerequisites").collect(anyPrerequisite ? "present" : "absent");

        Statistics.label("outcome").coverage(coverage -> {
            coverage.check("all scheduled").percentage(p -> p > 5.0);
            coverage.check("some dropped").percentage(p -> p > 5.0);
        });
        Statistics.label("multi-session topics").coverage(c -> c.check("present").percentage(p -> p > 5.0));
        Statistics.label("prerequisites").coverage(c -> c.check("present").percentage(p -> p > 5.0));
    }

    @Provide
    Arbitrary<PlanningRequest> requests() {
        Arbitrary<PlanningHorizon> horizons = Combinators.combine(
                        Arbitraries.integers().between(1, 10), Arbitraries.integers().between(0, 6))
                .as((weeks, reserved) -> PlanningHorizon.of(
                        PlanFixtures.MONDAY, weeks, Math.min(reserved, weeks * 7 - 1)));

        Arbitrary<PlanningConfig> configs = Combinators.combine(
                        Arbitraries.integers().between(1, 40),
                        Arbitraries.integers().between(20, 240),
                        Arbitraries.integers().between(1, 4),
                        Arbitraries.integers().between(0, 14))
                .as((weeklyHours, dailyCap, perDay, cadence) -> new PlanningConfig(
                        weeklyHours,
                        dailyCap,
                        perDay,
                        List.of(3, 10, 24),
                        Math.min(20, dailyCap),
                        cadence,
                        cadence == 0 ? 0 : Math.min(45, dailyCap)));

        return Combinators.combine(horizons, configs, targetLists()).as(PlanningRequest::of);
    }

    /** Backward-only references, so the generated prerequisite graph is acyclic by construction. */
    @Provide
    Arbitrary<List<PlannedSubSkill>> targetLists() {
        return Arbitraries.integers().between(0, 12).flatMap(size -> Arbitraries.randomValue(random -> {
            List<PlannedSubSkill> targets = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                List<SubSkillCode> prerequisites = new ArrayList<>();
                for (int earlier = 0; earlier < i; earlier++) {
                    if (random.nextInt(5) == 0) {
                        prerequisites.add(SubSkillCode.of("topic-" + earlier));
                    }
                }
                targets.add(PlannedSubSkill.of(
                        SubSkillCode.of("topic-" + i),
                        "Topic " + i,
                        random.nextInt(400) + 1,
                        prerequisites));
            }
            return targets;
        }));
    }
}
