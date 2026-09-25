package com.placefy.domain.roadmap;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.List;
import java.util.Objects;

/**
 * One sub-skill the planner has been asked to fit in, with the effort it takes and what must
 * come before it.
 *
 * <p>The planner does not decide which sub-skills belong in a plan, and does not read effort out
 * of the taxonomy itself. Both arrive here already decided — the set and its priority order come
 * from gap analysis, the effort from whatever rule converts taxonomy hours into planned minutes.
 * Keeping that outside means the planner has no opinion about scoring, and can be tested without
 * one existing.
 *
 * @param prerequisites codes that must be studied first. References to sub-skills outside this
 *     plan are ignored rather than pulled in: what to study is a gap-analysis decision, and a
 *     planner that silently added unrequested topics would be making it.
 */
public record PlannedSubSkill(
        SubSkillCode code, String name, int effortMinutes, List<SubSkillCode> prerequisites) {

    public PlannedSubSkill {
        Objects.requireNonNull(code, "code");
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "A planned sub-skill needs a name.");
        }
        if (effortMinutes < 1) {
            throw new DomainValidationException(
                    "effortMinutes", "Effort must be at least one minute, got " + effortMinutes + ".");
        }
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }

    public static PlannedSubSkill of(
            SubSkillCode code, String name, int effortMinutes, List<SubSkillCode> prerequisites) {
        return new PlannedSubSkill(code, name, effortMinutes, prerequisites);
    }
}
