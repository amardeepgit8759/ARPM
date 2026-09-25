package com.placefy.domain.roadmap;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.Objects;

/** A sub-skill the plan could not accommodate, and why. */
public record DroppedSubSkill(SubSkillCode code, String name, DropReason reason, String detail) {

    public DroppedSubSkill {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(reason, "reason");
        if (name == null || name.isBlank()) {
            throw new DomainValidationException("name", "A dropped sub-skill needs a name.");
        }
        detail = detail == null ? "" : detail;
    }
}
