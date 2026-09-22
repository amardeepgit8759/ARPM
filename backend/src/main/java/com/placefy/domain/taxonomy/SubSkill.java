package com.placefy.domain.taxonomy;

import java.util.List;
import java.util.Objects;

/**
 * The leaf of the taxonomy and the unit everything downstream attaches to: assessment item
 * tags, company requirement thresholds, and eventually scores and gaps.
 *
 * <p>Prerequisites are held as codes, not as resolved {@code SubSkill} objects. They routinely
 * point across skill and domain boundaries, so resolving them here would either require the
 * whole taxonomy to build one leaf, or permit an object graph with dangling references. The
 * aggregate resolves and validates them once, for all of them, in one place.
 */
public record SubSkill(SubSkillCode code, NodeName name, EffortHours defaultEffortHours, List<SubSkillCode> prerequisites) {

    public SubSkill {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(defaultEffortHours, "defaultEffortHours");
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }

    public static SubSkill of(
            SubSkillCode code, NodeName name, EffortHours defaultEffortHours, List<SubSkillCode> prerequisites) {
        return new SubSkill(code, name, defaultEffortHours, prerequisites);
    }

    public boolean hasPrerequisites() {
        return !prerequisites.isEmpty();
    }
}
