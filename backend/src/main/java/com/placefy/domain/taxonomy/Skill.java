package com.placefy.domain.taxonomy;

import java.util.List;
import java.util.Objects;

public record Skill(SkillCode code, NodeName name, List<SubSkill> subSkills) {

    public Skill {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        subSkills = subSkills == null ? List.of() : List.copyOf(subSkills);
    }

    public static Skill of(SkillCode code, NodeName name, List<SubSkill> subSkills) {
        return new Skill(code, name, subSkills);
    }
}
