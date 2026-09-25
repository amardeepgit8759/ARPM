package com.placefy.domain.roadmap;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised when the requested sub-skills depend on each other in a loop.
 *
 * <p>A published taxonomy cannot contain a cycle — the content validator rejects one before it
 * ever reaches the database — so this indicates a request assembled from somewhere else, not bad
 * content. Failing loudly beats emitting a plan in an arbitrary order that looks fine.
 */
public class CyclicPrerequisitesException extends RuntimeException {

    private final transient List<SubSkillCode> involved;

    public CyclicPrerequisitesException(List<SubSkillCode> involved) {
        super("These sub-skills depend on each other in a loop, so no study order exists: "
                + involved.stream().map(SubSkillCode::value).collect(Collectors.joining(", ")));
        this.involved = List.copyOf(involved);
    }

    public List<SubSkillCode> involved() {
        return involved;
    }
}
