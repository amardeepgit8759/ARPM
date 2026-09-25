package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * What a question measures, and how much of it.
 *
 * <p>The weight is carried, validated as positive, and otherwise left alone. Whether a
 * question's weights must sum to one, and how they are normalised if not, is an open question in
 * {@code docs/scoring-spec.md} §5.2 — so this deliberately neither enforces a sum nor rescales
 * anything. Normalising here would quietly answer a question nobody has answered yet, and every
 * score computed afterwards would inherit that guess.
 */
public record SubSkillTag(SubSkillCode subSkill, BigDecimal weight) {

    public SubSkillTag {
        Objects.requireNonNull(subSkill, "subSkill");
        if (weight == null) {
            throw new DomainValidationException("tagWeight", "Tag '" + subSkill + "' has no weight.");
        }
        if (weight.signum() <= 0) {
            throw new DomainValidationException(
                    "tagWeight", "Tag '" + subSkill + "' must have a positive weight, got " + weight + ".");
        }
    }

    public static SubSkillTag of(SubSkillCode subSkill, BigDecimal weight) {
        return new SubSkillTag(subSkill, weight);
    }
}
