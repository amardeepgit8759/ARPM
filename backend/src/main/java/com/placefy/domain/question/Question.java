package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * One reviewed, attested question.
 *
 * <p>Immutable, and it validates its own internal consistency: a single-choice question with two
 * correct answers cannot be constructed, nor can one whose correct answer names an option that
 * does not exist. Cross-question rules — unique codes, duplicate stems, tags resolving against a
 * taxonomy — belong to {@link QuestionBank}, because no question can check them alone.
 */
public record Question(
        QuestionCode code,
        QuestionType type,
        Difficulty difficulty,
        String stem,
        List<QuestionOption> options,
        List<String> correctOptionIds,
        String explanation,
        List<SubSkillTag> tags,
        OriginalityAttestation originality,
        ReviewerSignOff review) {

    private static final int MIN_STEM = 15;
    private static final int MAX_STEM = 4000;
    private static final int MIN_EXPLANATION = 20;
    private static final int MAX_EXPLANATION = 4000;
    private static final int MIN_OPTIONS = 2;

    public Question {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(originality, "originality");
        Objects.requireNonNull(review, "review");

        stem = requireText("stem", stem, MIN_STEM, MAX_STEM);
        // An explanation is required because a wrong answer with no reason given teaches nothing,
        // and this bank exists to move a score that a student's study plan is built from.
        explanation = requireText("explanation", explanation, MIN_EXPLANATION, MAX_EXPLANATION);

        options = options == null ? List.of() : List.copyOf(options);
        correctOptionIds = correctOptionIds == null ? List.of() : List.copyOf(correctOptionIds);
        tags = tags == null ? List.of() : List.copyOf(tags);

        validateOptions(code, options);
        validateCorrectAnswers(code, type, options, correctOptionIds);
        validateTags(code, tags);

        if (!originality.originalWork()) {
            throw new DomainValidationException(
                    "originalWork",
                    "Question '" + code + "' is not attested as original work. There is no approved route "
                            + "into the bank for a question reproduced from somewhere else.");
        }
        if (!review.isApproved()) {
            throw new DomainValidationException(
                    "reviewOutcome",
                    "Question '" + code + "' is " + review.outcome() + ", not APPROVED, so it cannot be asked.");
        }
        if (review.reviewedBy().equalsIgnoreCase(originality.attestedBy())) {
            throw new DomainValidationException(
                    "reviewedBy",
                    "Question '" + code + "' was signed off by its own author (" + review.reviewedBy()
                            + "). Self-review is not review.");
        }
        if (review.reviewedOn().isBefore(originality.attestedOn())) {
            throw new DomainValidationException(
                    "reviewedOn",
                    "Question '" + code + "' was reviewed on " + review.reviewedOn()
                            + ", before it was written on " + originality.attestedOn() + ".");
        }
    }

    public StemFingerprint fingerprint() {
        return StemFingerprint.of(stem);
    }

    public List<SubSkillCode> taggedSubSkills() {
        return tags.stream().map(SubSkillTag::subSkill).toList();
    }

    public boolean covers(SubSkillCode subSkill) {
        return tags.stream().anyMatch(tag -> tag.subSkill().equals(subSkill));
    }

    private static void validateOptions(QuestionCode code, List<QuestionOption> options) {
        if (options.size() < MIN_OPTIONS) {
            throw new DomainValidationException(
                    "options", "Question '" + code + "' needs at least " + MIN_OPTIONS + " options.");
        }

        Set<String> ids = new HashSet<>();
        Set<String> texts = new HashSet<>();
        for (QuestionOption option : options) {
            if (!ids.add(option.id())) {
                throw new DomainValidationException(
                        "options", "Question '" + code + "' repeats option id '" + option.id() + "'.");
            }
            // Two identically worded options make at least one of them unanswerable.
            if (!texts.add(option.text().toLowerCase(Locale.ROOT))) {
                throw new DomainValidationException(
                        "options", "Question '" + code + "' has two options with the same text.");
            }
        }
    }

    private static void validateCorrectAnswers(
            QuestionCode code, QuestionType type, List<QuestionOption> options, List<String> correctOptionIds) {

        if (correctOptionIds.isEmpty()) {
            throw new DomainValidationException(
                    "correctOptions", "Question '" + code + "' has no correct answer.");
        }

        Set<String> optionIds = new HashSet<>();
        options.forEach(option -> optionIds.add(option.id()));

        Set<String> seen = new HashSet<>();
        for (String correct : correctOptionIds) {
            if (!optionIds.contains(correct)) {
                throw new DomainValidationException(
                        "correctOptions",
                        "Question '" + code + "' marks '" + correct + "' correct, but no such option exists.");
            }
            if (!seen.add(correct)) {
                throw new DomainValidationException(
                        "correctOptions", "Question '" + code + "' lists '" + correct + "' as correct twice.");
            }
        }

        if (correctOptionIds.size() == options.size()) {
            throw new DomainValidationException(
                    "correctOptions", "Question '" + code + "' marks every option correct, so it asks nothing.");
        }

        if (type == QuestionType.SINGLE_CHOICE && correctOptionIds.size() != 1) {
            throw new DomainValidationException(
                    "correctOptions",
                    "Question '" + code + "' is SINGLE_CHOICE but marks " + correctOptionIds.size()
                            + " options correct.");
        }
        if (type == QuestionType.MULTIPLE_CHOICE && correctOptionIds.size() < 2) {
            throw new DomainValidationException(
                    "correctOptions",
                    "Question '" + code + "' is MULTIPLE_CHOICE but marks only one option correct. "
                            + "Use SINGLE_CHOICE.");
        }
    }

    private static void validateTags(QuestionCode code, List<SubSkillTag> tags) {
        if (tags.isEmpty()) {
            throw new DomainValidationException(
                    "tags",
                    "Question '" + code + "' has no sub-skill tags, so answering it could not move any score.");
        }

        Set<SubSkillCode> seen = new HashSet<>();
        for (SubSkillTag tag : tags) {
            if (!seen.add(tag.subSkill())) {
                throw new DomainValidationException(
                        "tags", "Question '" + code + "' tags '" + tag.subSkill() + "' twice.");
            }
        }
    }

    private static String requireText(String field, String value, int min, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.length() < min) {
            throw new DomainValidationException(
                    field, field + " must be at least " + min + " characters, got " + stripped.length() + ".");
        }
        if (stripped.length() > max) {
            throw new DomainValidationException(field, field + " must be at most " + max + " characters.");
        }
        return stripped;
    }
}
