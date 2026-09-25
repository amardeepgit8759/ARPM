package com.placefy.domain.roadmap;

import static com.placefy.domain.roadmap.PlanFixtures.MONDAY;
import static com.placefy.domain.roadmap.PlanFixtures.config;
import static com.placefy.domain.roadmap.PlanFixtures.configWithoutCheckpoints;
import static com.placefy.domain.roadmap.PlanFixtures.horizon;
import static com.placefy.domain.roadmap.PlanFixtures.request;
import static com.placefy.domain.roadmap.PlanFixtures.subSkill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RoadmapPlannerTest {

    private final RoadmapPlanner planner = new RoadmapPlanner();

    private static LocalDate firstStudyDateOf(Roadmap roadmap, String code) {
        return roadmap.days().stream()
                .filter(day -> day.tasks().stream()
                        .anyMatch(task -> task.isStudy() && task.subSkillCode().value().equals(code)))
                .map(RoadmapDay::date)
                .findFirst()
                .orElseThrow(() -> new AssertionError(code + " was never scheduled"));
    }

    @Nested
    class Ordering {

        @Test
        @DisplayName("a prerequisite is never studied after what depends on it")
        void schedulesPrerequisitesFirst() {
            Roadmap roadmap = planner.plan(request(
                    horizon(4, 2),
                    config(),
                    // Deliberately given in the wrong order: priority says graphs first, but
                    // graphs cannot be studied before trees.
                    subSkill("graph-traversal", 120, "tree-traversal"),
                    subSkill("tree-traversal", 120)));

            assertThat(firstStudyDateOf(roadmap, "tree-traversal"))
                    .isBeforeOrEqualTo(firstStudyDateOf(roadmap, "graph-traversal"));
        }

        @Test
        @DisplayName("among available topics, the caller's priority decides")
        void breaksTiesByPriority() {
            Roadmap roadmap = planner.plan(request(
                    horizon(4, 2), config(), subSkill("second-priority", 60), subSkill("third-priority", 60)));

            assertThat(firstStudyDateOf(roadmap, "second-priority"))
                    .isBeforeOrEqualTo(firstStudyDateOf(roadmap, "third-priority"));
        }

        @Test
        @DisplayName("a prerequisite outside the plan is ignored, not pulled in")
        void ignoresPrerequisitesOutsideThePlan() {
            Roadmap roadmap = planner.plan(request(
                    horizon(4, 2), config(), subSkill("graph-traversal", 60, "not-in-this-plan")));

            assertThat(roadmap.scheduledSubSkills())
                    .containsExactly(SubSkillCode.of("graph-traversal"));
        }

        @Test
        void refusesACycleRatherThanGuessingAnOrder() {
            assertThatThrownBy(() -> planner.plan(request(
                            horizon(4, 2),
                            config(),
                            subSkill("alpha", 60, "beta"),
                            subSkill("beta", 60, "alpha"))))
                    .isInstanceOf(CyclicPrerequisitesException.class)
                    .hasMessageContaining("alpha")
                    .hasMessageContaining("beta");
        }
    }

    @Nested
    class Capacity {

        @Test
        @DisplayName("a topic larger than one day is split across days, never truncated")
        void splitsLongTopicsIntoSessions() {
            Roadmap roadmap =
                    planner.plan(request(horizon(4, 2), configWithoutCheckpoints(), subSkill("big-topic", 200)));

            assertThat(roadmap.studyMinutesFor(SubSkillCode.of("big-topic"))).isEqualTo(200);
            assertThat(roadmap.droppedSubSkills()).isEmpty();
        }

        @Test
        @DisplayName("sessions are numbered so a split topic reads as one topic")
        void numbersSessions() {
            Roadmap roadmap =
                    planner.plan(request(horizon(4, 2), configWithoutCheckpoints(), subSkill("big-topic", 200)));

            List<RoadmapTask> sessions = roadmap.days().stream()
                    .flatMap(day -> day.tasks().stream())
                    .filter(RoadmapTask::isStudy)
                    .toList();

            assertThat(sessions).hasSize(3);
            assertThat(sessions).allSatisfy(task -> assertThat(task.sessionCount()).isEqualTo(3));
            assertThat(sessions).extracting(RoadmapTask::sessionIndex).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("what will not fit is dropped whole and named, never compressed")
        void dropsRatherThanCompresses() {
            // One week at 10 hours is 600 minutes. Three 400-minute topics cannot all fit.
            Roadmap roadmap = planner.plan(request(
                    horizon(1, 1),
                    configWithoutCheckpoints(),
                    subSkill("first", 400),
                    subSkill("second", 400),
                    subSkill("third", 400)));

            assertThat(roadmap.droppedSubSkills()).isNotEmpty();
            assertThat(roadmap.isComplete()).isFalse();

            // Whatever survived kept its full effort.
            for (SubSkillCode scheduled : roadmap.scheduledSubSkills()) {
                assertThat(roadmap.studyMinutesFor(scheduled)).isEqualTo(400);
            }
        }

        @Test
        @DisplayName("a dropped prerequisite drops what depends on it, with that stated as the reason")
        void cascadesDropsToDependents() {
            Roadmap roadmap = planner.plan(request(
                    horizon(1, 1),
                    configWithoutCheckpoints(),
                    subSkill("huge-prerequisite", 5000),
                    subSkill("depends-on-it", 30, "huge-prerequisite")));

            assertThat(roadmap.droppedSubSkills())
                    .extracting(DroppedSubSkill::reason)
                    .containsExactly(DropReason.EXCEEDS_REMAINING_CAPACITY, DropReason.PREREQUISITE_DROPPED);
            assertThat(roadmap.droppedSubSkills().get(1).detail()).contains("huge-prerequisite");
        }

        @Test
        @DisplayName("a topic that does not fit leaves no half-session behind")
        void doesNotLeavePartialStudyForADroppedTopic() {
            Roadmap roadmap = planner.plan(
                    request(horizon(1, 1), configWithoutCheckpoints(), subSkill("far-too-big", 5000)));

            assertThat(roadmap.scheduledSubSkills()).isEmpty();
            assertThat(roadmap.totalPlannedMinutes()).isZero();
        }

        @Test
        @DisplayName("the weekly budget caps a week even when daily room remains")
        void respectsTheWeeklyBudget() {
            // 10 hours a week = 600 minutes, well below 7 days x 90 minutes of daily room.
            Roadmap roadmap = planner.plan(request(
                    horizon(1, 0), configWithoutCheckpoints(), subSkill("fills-the-week", 630)));

            assertThat(roadmap.droppedSubSkills()).hasSize(1);
        }
    }

    @Nested
    class FinalRevisionWindow {

        @Test
        @DisplayName("no new material is scheduled in the reserved window")
        void keepsTheFinalWindowFreeOfNewStudy() {
            PlanningHorizon horizon = horizon(3, 5);
            Roadmap roadmap = planner.plan(request(
                    horizon,
                    config(),
                    subSkill("one", 300),
                    subSkill("two", 300),
                    subSkill("three", 300),
                    subSkill("four", 300)));

            roadmap.days().stream()
                    .filter(day -> horizon.isReservedForRevision(day.date()))
                    .forEach(day -> assertThat(day.tasks())
                            .as("new study on reserved day %s", day.date())
                            .noneMatch(RoadmapTask::isStudy));
        }
    }

    @Nested
    class Revision {

        @Test
        @DisplayName("a studied topic comes back at each configured offset")
        void schedulesSpacedRevision() {
            Roadmap roadmap =
                    planner.plan(request(horizon(6, 3), configWithoutCheckpoints(), subSkill("topic", 60)));

            LocalDate studied = firstStudyDateOf(roadmap, "topic");
            for (int offset : List.of(3, 10, 24)) {
                assertThat(roadmap.dayOn(studied.plusDays(offset)).orElseThrow().tasks())
                        .as("+%d day revision", offset)
                        .anyMatch(task -> task.type() == RoadmapTaskType.REVISION);
            }
        }

        @Test
        @DisplayName("a revision whose offset lands past the horizon is reported, not dropped in silence")
        void reportsRevisionThatFallsOutsideTheHorizon() {
            Roadmap roadmap =
                    planner.plan(request(horizon(2, 1), configWithoutCheckpoints(), subSkill("topic", 60)));

            assertThat(roadmap.unscheduledRevisions())
                    .isNotEmpty()
                    .allSatisfy(missed -> assertThat(missed.reason()).isNotBlank());
            assertThat(roadmap.unscheduledRevisions())
                    .extracting(UnscheduledRevision::offsetDays)
                    .contains(24);
        }

        @Test
        @DisplayName("revision may sit in the reserved final window; that is what it is for")
        void revisionMayUseTheReservedWindow() {
            // 14 days, the last 3 reserved. A 700-minute topic exhausts week one's 600-minute
            // budget and spills into week two, finishing on day 8 — so its +3 review lands on
            // day 11, inside the window. The window bars new study, not revisiting.
            PlanningHorizon horizon = horizon(2, 3);
            Roadmap roadmap = planner.plan(request(horizon, configWithoutCheckpoints(), subSkill("topic", 700)));

            boolean anyRevisionInWindow = roadmap.days().stream()
                    .filter(day -> horizon.isReservedForRevision(day.date()))
                    .flatMap(day -> day.tasks().stream())
                    .anyMatch(task -> task.type() == RoadmapTaskType.REVISION);

            assertThat(anyRevisionInWindow).isTrue();
        }
    }

    @Nested
    class Checkpoints {

        @Test
        void placesCheckpointsOnCadence() {
            Roadmap roadmap = planner.plan(request(horizon(3, 2), config(), subSkill("topic", 60)));

            List<LocalDate> checkpointDates = roadmap.days().stream()
                    .filter(day -> day.tasks().stream().anyMatch(t -> t.type() == RoadmapTaskType.CHECKPOINT))
                    .map(RoadmapDay::date)
                    .toList();

            assertThat(checkpointDates)
                    .containsExactly(MONDAY.plusDays(7), MONDAY.plusDays(14));
        }

        @Test
        void placesNoneWhenTheCadenceIsZero() {
            Roadmap roadmap =
                    planner.plan(request(horizon(3, 2), configWithoutCheckpoints(), subSkill("topic", 60)));

            assertThat(roadmap.days())
                    .allSatisfy(day -> assertThat(day.tasks())
                            .noneMatch(task -> task.type() == RoadmapTaskType.CHECKPOINT));
        }
    }

    @Nested
    class Emptiness {

        @Test
        @DisplayName("a plan with nothing to study is still a valid, empty plan")
        void handlesAnEmptyTargetList() {
            Roadmap roadmap = planner.plan(PlanningRequest.of(horizon(2, 1), config(), List.of()));

            assertThat(roadmap.scheduledSubSkills()).isEmpty();
            assertThat(roadmap.droppedSubSkills()).isEmpty();
            assertThat(roadmap.days()).hasSize(14);
            assertThat(roadmap.isComplete()).isTrue();
        }
    }
}
