package com.placefy.domain.roadmap;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orders the requested sub-skills so nothing is studied before what it depends on.
 *
 * <p>Kahn's algorithm, but the choice among currently-available nodes is settled by the caller's
 * priority order rather than by whatever order a hash map happens to produce. That matters twice
 * over: it puts the most valuable available topic first, and it makes the output a pure function
 * of the input, which is what the stability property test asserts.
 *
 * <p>Prerequisites pointing outside the request are ignored. The set of sub-skills to study is a
 * gap-analysis decision, and pulling in unrequested topics here would quietly override it.
 */
final class PrerequisiteOrdering {

    private PrerequisiteOrdering() {}

    static List<PlannedSubSkill> order(List<PlannedSubSkill> targets) {
        Map<SubSkillCode, PlannedSubSkill> byCode = new LinkedHashMap<>();
        targets.forEach(target -> byCode.put(target.code(), target));
        Set<SubSkillCode> inPlan = byCode.keySet();

        // Priority is the caller's ordering; index 0 is the most important.
        Map<SubSkillCode, Integer> priority = new LinkedHashMap<>();
        for (int i = 0; i < targets.size(); i++) {
            priority.put(targets.get(i).code(), i);
        }

        Map<SubSkillCode, List<SubSkillCode>> blockedBy = new LinkedHashMap<>();
        for (PlannedSubSkill target : targets) {
            List<SubSkillCode> relevant = target.prerequisites().stream()
                    .filter(inPlan::contains)
                    .filter(prerequisite -> !prerequisite.equals(target.code()))
                    .toList();
            blockedBy.put(target.code(), new ArrayList<>(relevant));
        }

        List<PlannedSubSkill> ordered = new ArrayList<>();
        List<SubSkillCode> remaining = new ArrayList<>(byCode.keySet());

        while (!remaining.isEmpty()) {
            SubSkillCode next = null;
            for (SubSkillCode candidate : remaining) {
                if (blockedBy.get(candidate).isEmpty()
                        && (next == null || priority.get(candidate) < priority.get(next))) {
                    next = candidate;
                }
            }

            if (next == null) {
                // The taxonomy guarantees an acyclic prerequisite graph, so reaching here means a
                // caller assembled a request the taxonomy could not have produced.
                throw new CyclicPrerequisitesException(List.copyOf(remaining));
            }

            ordered.add(byCode.get(next));
            remaining.remove(next);
            SubSkillCode placed = next;
            blockedBy.values().forEach(blockers -> blockers.remove(placed));
        }

        return List.copyOf(ordered);
    }
}
