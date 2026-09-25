package com.placefy.domain.narrative;

import java.util.Objects;

/**
 * One reason a narrative was refused.
 *
 * @param offendingText the exact text at fault, quoted back so a rejection can be diagnosed from
 *     a log line without re-running the generation
 */
public record NarrativeViolation(NarrativeViolationType type, String message, String offendingText) {

    public NarrativeViolation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(message, "message");
        offendingText = offendingText == null ? "" : offendingText;
    }

    static NarrativeViolation unlistedNumber(NumericToken token) {
        return new NarrativeViolation(
                NarrativeViolationType.UNLISTED_NUMBER,
                "The narrative states " + token.value().toPlainString()
                        + ", which this scoring run did not produce.",
                token.text());
    }

    static NarrativeViolation subSkillNotInTrace(String name) {
        return new NarrativeViolation(
                NarrativeViolationType.SUB_SKILL_NOT_IN_TRACE,
                "The narrative names '" + name + "', which is not among this run's gaps or strengths.",
                name);
    }

    static NarrativeViolation missingSection(String heading) {
        return new NarrativeViolation(
                NarrativeViolationType.MISSING_SECTION, "Required section '" + heading + "' is absent.", heading);
    }

    static NarrativeViolation unexpectedSection(String heading) {
        return new NarrativeViolation(
                NarrativeViolationType.UNEXPECTED_SECTION,
                "Section '" + heading + "' is not part of the expected format.",
                heading);
    }

    static NarrativeViolation tooShort(int length, int minimum) {
        return new NarrativeViolation(
                NarrativeViolationType.TOO_SHORT,
                "The narrative is " + length + " characters; at least " + minimum + " are required.",
                "");
    }

    static NarrativeViolation tooLong(int length, int maximum) {
        return new NarrativeViolation(
                NarrativeViolationType.TOO_LONG,
                "The narrative is " + length + " characters; at most " + maximum + " are allowed.",
                "");
    }

    static NarrativeViolation empty() {
        return new NarrativeViolation(NarrativeViolationType.EMPTY, "Nothing was generated.", "");
    }

    @Override
    public String toString() {
        return "[" + type + "] " + message;
    }
}
