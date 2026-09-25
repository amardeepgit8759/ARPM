package com.placefy.domain.roadmap;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything the planner needs, and nothing it could read from anywhere else.
 *
 * @param targets in priority order, highest first. The planner reorders them to respect
 *     prerequisites and uses this order only to break ties, so a request that already happens to
 *     be a valid study order comes back in exactly that order.
 */
public record PlanningRequest(PlanningHorizon horizon, PlanningConfig config, List<PlannedSubSkill> targets) {

    public PlanningRequest {
        Objects.requireNonNull(horizon, "horizon");
        Objects.requireNonNull(config, "config");
        targets = targets == null ? List.of() : List.copyOf(targets);

        Set<SubSkillCode> seen = new HashSet<>();
        for (PlannedSubSkill target : targets) {
            if (!seen.add(target.code())) {
                throw new DomainValidationException(
                        "targets", "Sub-skill '" + target.code() + "' appears twice in the plan request.");
            }
        }
    }

    public static PlanningRequest of(
            PlanningHorizon horizon, PlanningConfig config, List<PlannedSubSkill> targets) {
        return new PlanningRequest(horizon, config, targets);
    }
}
