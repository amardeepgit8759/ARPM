package com.placefy.domain.roadmap;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan requests for the roadmap tests.
 *
 * <p>The pacing values here are test data. They are not defaults and the planner has none — every
 * one of them is supplied by the caller, because pacing rules belong in versioned configuration
 * rather than in the algorithm.
 */
final class PlanFixtures {

    static final LocalDate MONDAY = LocalDate.parse("2026-09-21");

    private PlanFixtures() {}

    /** The offsets named in the brief: +3, +10, +24 days. */
    static PlanningConfig config() {
        return new PlanningConfig(10, 90, 2, List.of(3, 10, 24), 20, 7, 45);
    }

    static PlanningConfig configWithoutCheckpoints() {
        return new PlanningConfig(10, 90, 2, List.of(3, 10, 24), 20, 0, 0);
    }

    static PlanningHorizon horizon(int weeks, int finalRevisionDays) {
        return PlanningHorizon.of(MONDAY, weeks, finalRevisionDays);
    }

    static PlannedSubSkill subSkill(String code, int minutes, String... prerequisites) {
        List<SubSkillCode> prereqs = new ArrayList<>();
        for (String prerequisite : prerequisites) {
            prereqs.add(SubSkillCode.of(prerequisite));
        }
        return PlannedSubSkill.of(SubSkillCode.of(code), nameFor(code), minutes, prereqs);
    }

    static String nameFor(String code) {
        String[] words = code.split("-");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return name.toString().strip();
    }

    static PlanningRequest request(PlanningHorizon horizon, PlanningConfig config, PlannedSubSkill... targets) {
        return PlanningRequest.of(horizon, config, List.of(targets));
    }
}
