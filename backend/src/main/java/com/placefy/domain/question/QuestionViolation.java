package com.placefy.domain.question;

import java.util.Objects;

/**
 * One reason a bank is invalid, located in content terms.
 *
 * @param location a question code or file name — never a Java class, because the person fixing
 *     this is editing YAML
 */
public record QuestionViolation(QuestionViolationType type, String location, String message) {

    public QuestionViolation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    static QuestionViolation duplicateCode(QuestionCode code) {
        return new QuestionViolation(
                QuestionViolationType.DUPLICATE_QUESTION_CODE,
                code.value(),
                "Question code '" + code + "' is used more than once. Codes are the identity a stored "
                        + "response points at, so they cannot be shared.");
    }

    static QuestionViolation duplicateStem(QuestionCode first, QuestionCode second) {
        return new QuestionViolation(
                QuestionViolationType.DUPLICATE_STEM,
                second.value(),
                "Question '" + second + "' asks the same thing as '" + first
                        + "'. The stems are identical once case, punctuation and spacing are ignored.");
    }

    static QuestionViolation nearDuplicateStem(
            QuestionCode first, QuestionCode second, double similarity, double threshold) {
        return new QuestionViolation(
                QuestionViolationType.NEAR_DUPLICATE_STEM,
                second.value(),
                "Question '" + second + "' shares " + Math.round(similarity * 100)
                        + "% of its phrasing with '" + first + "', above the "
                        + Math.round(threshold * 100) + "% threshold. Reword it or retire one of the two.");
    }

    static QuestionViolation unknownTag(QuestionCode code, String subSkill, String taxonomyVersion) {
        return new QuestionViolation(
                QuestionViolationType.UNKNOWN_SUB_SKILL_TAG,
                code.value(),
                "Question '" + code + "' is tagged '" + subSkill + "', which taxonomy version "
                        + taxonomyVersion + " does not contain.");
    }

    static QuestionViolation taxonomyMismatch(String declared, String actual) {
        return new QuestionViolation(
                QuestionViolationType.TAXONOMY_VERSION_MISMATCH,
                "questions.yaml",
                "The bank declares taxonomy version '" + declared + "' but was validated against '" + actual
                        + "'. Tags cannot be checked against a different vocabulary than the one they were "
                        + "written for.");
    }

    public static QuestionViolation malformed(String location, String message) {
        return new QuestionViolation(QuestionViolationType.MALFORMED_CONTENT, location, message);
    }

    @Override
    public String toString() {
        return "[" + type + "] " + location + ": " + message;
    }
}
