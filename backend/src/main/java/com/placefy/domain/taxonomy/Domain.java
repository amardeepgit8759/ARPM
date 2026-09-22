package com.placefy.domain.taxonomy;

import java.util.List;
import java.util.Objects;

/** A top-level area of the taxonomy, such as data structures and algorithms. */
public record Domain(DomainCode code, NodeName name, List<Skill> skills) {

    public Domain {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public static Domain of(DomainCode code, NodeName name, List<Skill> skills) {
        return new Domain(code, name, skills);
    }
}
