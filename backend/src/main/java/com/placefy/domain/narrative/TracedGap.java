package com.placefy.domain.narrative;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One ranked gap, with the three numbers that justify it: where the student is, where the
 * target expects them to be, and how much that difference matters.
 *
 * <p>Carrying the threshold alongside the score is what lets a narrative say why this gap
 * outranks another without the model doing arithmetic — both numbers are already on the page.
 */
public record TracedGap(
        SubSkillCode subSkillCode,
        String subSkillName,
        BigDecimal score,
        BigDecimal threshold,
        GapCriticality criticality) {

    public TracedGap {
        Objects.requireNonNull(subSkillCode, "subSkillCode");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(criticality, "criticality");
        if (subSkillName == null || subSkillName.isBlank()) {
            throw new DomainValidationException("subSkillName", "A traced gap needs a sub-skill name.");
        }
    }

    public static TracedGap of(
            SubSkillCode subSkillCode,
            String subSkillName,
            BigDecimal score,
            BigDecimal threshold,
            GapCriticality criticality) {
        return new TracedGap(subSkillCode, subSkillName, score, threshold, criticality);
    }
}
