package com.placefy.domain.roadmap;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A dated plan, plus an honest account of everything that did not fit.
 *
 * <p>The three lists are the whole point. A roadmap that showed only what was scheduled would let
 * a student believe their plan covers their gaps when a third of them were quietly left out.
 */
public record Roadmap(
        PlanningHorizon horizon,
        PlanningConfig config,
        List<RoadmapDay> days,
        List<DroppedSubSkill> droppedSubSkills,
        List<UnscheduledRevision> unscheduledRevisions) {

    public Roadmap {
        Objects.requireNonNull(horizon, "horizon");
        Objects.requireNonNull(config, "config");
        days = days == null ? List.of() : List.copyOf(days);
        droppedSubSkills = droppedSubSkills == null ? List.of() : List.copyOf(droppedSubSkills);
        unscheduledRevisions = unscheduledRevisions == null ? List.of() : List.copyOf(unscheduledRevisions);
    }

    /** Days that actually carry work, in date order. */
    public List<RoadmapDay> scheduledDays() {
        return days.stream().filter(day -> !day.isEmpty()).toList();
    }

    public Optional<RoadmapDay> dayOn(LocalDate date) {
        return days.stream().filter(day -> day.date().equals(date)).findFirst();
    }

    public List<SubSkillCode> scheduledSubSkills() {
        return days.stream()
                .flatMap(day -> day.tasks().stream())
                .filter(RoadmapTask::isStudy)
                .map(RoadmapTask::subSkillCode)
                .distinct()
                .toList();
    }

    /** Minutes of new study planned for one sub-skill, summed across however many sessions. */
    public int studyMinutesFor(SubSkillCode code) {
        return days.stream()
                .flatMap(day -> day.tasks().stream())
                .filter(RoadmapTask::isStudy)
                .filter(task -> code.equals(task.subSkillCode()))
                .mapToInt(RoadmapTask::minutes)
                .sum();
    }

    public int totalPlannedMinutes() {
        return days.stream().mapToInt(RoadmapDay::totalMinutes).sum();
    }

    public boolean isComplete() {
        return droppedSubSkills.isEmpty();
    }
}
