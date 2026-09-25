package com.placefy.domain.narrative;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.util.Objects;

/** A sub-skill the run identified as a strength, with the score behind that claim. */
public record TracedStrength(SubSkillCode subSkillCode, String subSkillName, BigDecimal score) {

    public TracedStrength {
        Objects.requireNonNull(subSkillCode, "subSkillCode");
        Objects.requireNonNull(score, "score");
        if (subSkillName == null || subSkillName.isBlank()) {
            throw new DomainValidationException("subSkillName", "A traced strength needs a sub-skill name.");
        }
    }

    public static TracedStrength of(SubSkillCode subSkillCode, String subSkillName, BigDecimal score) {
        return new TracedStrength(subSkillCode, subSkillName, score);
    }
}
