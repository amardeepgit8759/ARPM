package com.placefy.domain.narrative;

import java.util.List;

/**
 * Turns a trace into readable prose without a model.
 *
 * <p>This is what a student sees when generation is switched off, when the provider is failing,
 * or when a generated draft is refused — which is why the product's promise to explain itself
 * does not depend on any external service being reachable.
 *
 * <p>It performs no arithmetic whatsoever. Every figure it prints is a value lifted straight out
 * of the trace, so its output satisfies the validator by construction rather than by care, and a
 * test asserts exactly that.
 */
public final class DecisionTraceRenderer {

    public static final String SECTION_STANDING = "Where you stand";
    public static final String SECTION_GAPS = "Your biggest gaps";
    public static final String SECTION_STRENGTHS = "What is working";
    public static final String SECTION_CONSISTENCY = "Consistency";

    /**
     * The format this renderer satisfies. Generous bounds: a trace with no gaps renders far
     * shorter than one with ten, and a length rule that its own fallback cannot meet would be a
     * rule that fires exactly when everything else has already gone wrong.
     */
    public static NarrativeFormat format() {
        return NarrativeFormat.of(
                List.of(SECTION_STANDING, SECTION_GAPS, SECTION_STRENGTHS, SECTION_CONSISTENCY), 60, 8000);
    }

    public String render(DecisionTrace trace) {
        StringBuilder out = new StringBuilder();

        heading(out, SECTION_STANDING);
        out.append(trace.overallScore().label())
                .append(": ")
                .append(trace.overallScore().rendered())
                .append(".")
                .append(System.lineSeparator());
        out.append(trace.targetMatch().label())
                .append(": ")
                .append(trace.targetMatch().rendered())
                .append(".")
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        heading(out, SECTION_GAPS);
        if (trace.topGaps().isEmpty()) {
            // Not "0 gaps". An absent measurement and a measured zero are different claims, and
            // the whole point of this layer is not to blur them.
            out.append("No gaps were ranked for this run.").append(System.lineSeparator());
        } else {
            int position = 1;
            for (TracedGap gap : trace.topGaps()) {
                out.append(position++)
                        .append(". ")
                        .append(gap.subSkillName())
                        .append(" — your score ")
                        .append(gap.score().toPlainString())
                        .append(" against a target of ")
                        .append(gap.threshold().toPlainString())
                        .append(" (")
                        .append(describe(gap.criticality()))
                        .append(").")
                        .append(System.lineSeparator());
            }
        }
        out.append(System.lineSeparator());

        heading(out, SECTION_STRENGTHS);
        if (trace.strengths().isEmpty()) {
            out.append("No strengths were identified for this run.").append(System.lineSeparator());
        } else {
            for (TracedStrength strength : trace.strengths()) {
                out.append("- ")
                        .append(strength.subSkillName())
                        .append(" — score ")
                        .append(strength.score().toPlainString())
                        .append(".")
                        .append(System.lineSeparator());
            }
        }
        out.append(System.lineSeparator());

        heading(out, SECTION_CONSISTENCY);
        out.append(trace.consistency().label())
                .append(": ")
                .append(trace.consistency().rendered())
                .append(".")
                .append(System.lineSeparator());

        return out.toString().strip();
    }

    /**
     * Wording only. The criticality itself was decided by the requirement set, not here, and
     * these phrases carry no threshold of their own.
     */
    private static String describe(GapCriticality criticality) {
        return switch (criticality) {
            case BLOCKING -> "blocking for this role";
            case IMPORTANT -> "important for this role";
            case NICE_TO_HAVE -> "nice to have for this role";
        };
    }

    private static void heading(StringBuilder out, String text) {
        out.append("## ").append(text).append(System.lineSeparator());
    }
}
