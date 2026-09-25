package com.placefy.domain.roadmap;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One day of the plan. Tasks are in the order they should be worked through. */
public record RoadmapDay(LocalDate date, List<RoadmapTask> tasks) {

    public RoadmapDay {
        Objects.requireNonNull(date, "date");
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }

    public int totalMinutes() {
        return tasks.stream().mapToInt(RoadmapTask::minutes).sum();
    }

    /** Distinct sub-skills of new material, which is what the per-day cap limits. */
    public Set<SubSkillCode> newStudySubSkills() {
        Set<SubSkillCode> codes = new LinkedHashSet<>();
        tasks.stream().filter(RoadmapTask::isStudy).forEach(task -> codes.add(task.subSkillCode()));
        return codes;
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}
