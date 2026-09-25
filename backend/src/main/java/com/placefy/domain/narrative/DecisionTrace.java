package com.placefy.domain.narrative;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a narrative is allowed to know about one scoring run.
 *
 * <p>The trace is the boundary between computing and describing. A generator receives this and
 * nothing else; whatever it writes is checked back against this and nothing else. That is what
 * makes "the model explains the numbers rather than deciding them" a property of the code rather
 * than a hope about the prompt.
 *
 * <p>It computes nothing. Every figure arrives already calculated and already rounded by the
 * scoring engine, and is carried through verbatim.
 *
 * @param scoringRunId the run this describes, so a rendered explanation can always be traced
 *     back to the persisted numbers behind it
 * @param engineVersion the four version fields a scoring run records. They travel with the
 *     trace so an explanation produced today can be re-derived and checked years from now.
 */
public record DecisionTrace(
        UUID scoringRunId,
        String engineVersion,
        String configVersion,
        String taxonomyVersion,
        String inputsHash,
        TracedMeasure overallScore,
        TracedMeasure targetMatch,
        List<TracedGap> topGaps,
        List<TracedStrength> strengths,
        TracedMeasure consistency,
        AllowedNumbers allowedNumbers) {

    public DecisionTrace {
        Objects.requireNonNull(scoringRunId, "scoringRunId");
        Objects.requireNonNull(overallScore, "overallScore");
        Objects.requireNonNull(targetMatch, "targetMatch");
        Objects.requireNonNull(consistency, "consistency");
        Objects.requireNonNull(allowedNumbers, "allowedNumbers");
        requireText("engineVersion", engineVersion);
        requireText("configVersion", configVersion);
        requireText("taxonomyVersion", taxonomyVersion);
        requireText("inputsHash", inputsHash);

        topGaps = topGaps == null ? List.of() : List.copyOf(topGaps);
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
    }

    /**
     * Assembles a trace and derives its allowed numbers.
     *
     * <p>The permitted set is every figure the trace carries, plus numbers that describe the
     * trace's own shape: the count of gaps and of strengths, and the ordinals 1..n for each list.
     * Those are not scoring outputs — "the first of your three gaps" is a statement about this
     * document, true by construction — so withholding them would reject correct prose and teach
     * nobody anything.
     *
     * <p>Nothing else is added. No sums, no differences, no percentages derived from the values:
     * the moment the set contains a number the engine did not produce, the guarantee is gone.
     */
    public static DecisionTrace of(
            UUID scoringRunId,
            String engineVersion,
            String configVersion,
            String taxonomyVersion,
            String inputsHash,
            TracedMeasure overallScore,
            TracedMeasure targetMatch,
            List<TracedGap> topGaps,
            List<TracedStrength> strengths,
            TracedMeasure consistency) {

        List<TracedGap> gaps = topGaps == null ? List.of() : List.copyOf(topGaps);
        List<TracedStrength> wins = strengths == null ? List.of() : List.copyOf(strengths);

        Set<BigDecimal> permitted = new LinkedHashSet<>();
        permitted.add(overallScore.value());
        permitted.add(targetMatch.value());
        permitted.add(consistency.value());

        for (TracedGap gap : gaps) {
            permitted.add(gap.score());
            permitted.add(gap.threshold());
        }
        for (TracedStrength strength : wins) {
            permitted.add(strength.score());
        }

        permitted.addAll(structuralNumbers(Math.max(gaps.size(), wins.size())));

        return new DecisionTrace(
                scoringRunId,
                engineVersion,
                configVersion,
                taxonomyVersion,
                inputsHash,
                overallScore,
                targetMatch,
                gaps,
                wins,
                consistency,
                AllowedNumbers.of(permitted));
    }

    /** Counts and positions: 0 through n, so "three gaps" and "the second" both check out. */
    private static List<BigDecimal> structuralNumbers(int largestListSize) {
        List<BigDecimal> numbers = new ArrayList<>();
        for (int i = 0; i <= largestListSize; i++) {
            numbers.add(BigDecimal.valueOf(i));
        }
        return numbers;
    }

    public boolean mentions(SubSkillCode code) {
        return topGaps.stream().anyMatch(gap -> gap.subSkillCode().equals(code))
                || strengths.stream().anyMatch(strength -> strength.subSkillCode().equals(code));
    }

    public List<SubSkillCode> subSkillCodes() {
        List<SubSkillCode> codes = new ArrayList<>();
        topGaps.forEach(gap -> codes.add(gap.subSkillCode()));
        strengths.forEach(strength -> codes.add(strength.subSkillCode()));
        return List.copyOf(codes);
    }

    private static void requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, field + " is required on a decision trace.");
        }
    }
}
