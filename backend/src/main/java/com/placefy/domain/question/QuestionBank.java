package com.placefy.domain.question;

import com.placefy.domain.DomainValidationException;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every approved question, checked against itself and against a taxonomy version.
 *
 * <p>The rules here are the ones no single question can enforce alone: that codes are unique,
 * that no two questions ask the same thing, and that every tag names a sub-skill the declared
 * taxonomy actually contains.
 *
 * @param nearDuplicateThreshold phrasing overlap above which two stems are treated as the same
 *     question reworded. Supplied rather than fixed, because how aggressive to be is a judgement
 *     about the bank rather than a property of the algorithm.
 */
public final class QuestionBank {

    private final String bankVersion;
    private final String taxonomyVersion;
    private final List<Question> questions;

    private QuestionBank(String bankVersion, String taxonomyVersion, List<Question> questions) {
        this.bankVersion = bankVersion;
        this.taxonomyVersion = taxonomyVersion;
        this.questions = List.copyOf(questions);
    }

    /**
     * @throws InvalidQuestionBankException listing every problem found across the whole bank
     */
    public static QuestionBank of(
            String bankVersion,
            String taxonomyVersion,
            List<Question> questions,
            TaxonomyVersion taxonomy,
            double nearDuplicateThreshold) {

        requireText("bankVersion", bankVersion);
        requireText("taxonomyVersion", taxonomyVersion);
        if (nearDuplicateThreshold <= 0 || nearDuplicateThreshold > 1) {
            throw new DomainValidationException(
                    "nearDuplicateThreshold",
                    "The near-duplicate threshold must be between 0 and 1, got " + nearDuplicateThreshold + ".");
        }

        List<Question> all = questions == null ? List.of() : List.copyOf(questions);
        List<QuestionViolation> violations = new ArrayList<>();

        if (taxonomy != null && !taxonomy.label().value().equals(taxonomyVersion)) {
            violations.add(QuestionViolation.taxonomyMismatch(taxonomyVersion, taxonomy.label().value()));
        }

        findDuplicateCodes(all, violations);
        findDuplicateStems(all, nearDuplicateThreshold, violations);
        if (taxonomy != null) {
            findUnknownTags(all, taxonomy, taxonomyVersion, violations);
        }

        if (!violations.isEmpty()) {
            throw new InvalidQuestionBankException(violations);
        }

        return new QuestionBank(bankVersion, taxonomyVersion, all);
    }

    private static void findDuplicateCodes(List<Question> questions, List<QuestionViolation> violations) {
        Set<QuestionCode> seen = new LinkedHashSet<>();
        for (Question question : questions) {
            if (!seen.add(question.code())) {
                violations.add(QuestionViolation.duplicateCode(question.code()));
            }
        }
    }

    /**
     * Compares every pair. The bank is small enough that the quadratic cost is irrelevant beside
     * the cost of shipping the same question twice — a student who meets it in both a Quick Check
     * and a mock has been measured once and told they were measured twice.
     */
    private static void findDuplicateStems(
            List<Question> questions, double threshold, List<QuestionViolation> violations) {

        Map<String, QuestionCode> byNormalisedStem = new LinkedHashMap<>();
        List<Question> compared = new ArrayList<>();

        for (Question question : questions) {
            StemFingerprint fingerprint = question.fingerprint();

            QuestionCode exact = byNormalisedStem.get(fingerprint.normalised());
            if (exact != null) {
                violations.add(QuestionViolation.duplicateStem(exact, question.code()));
                continue;
            }
            byNormalisedStem.put(fingerprint.normalised(), question.code());

            for (Question earlier : compared) {
                double similarity = fingerprint.similarityTo(earlier.fingerprint());
                if (similarity >= threshold) {
                    violations.add(QuestionViolation.nearDuplicateStem(
                            earlier.code(), question.code(), similarity, threshold));
                    break;
                }
            }
            compared.add(question);
        }
    }

    private static void findUnknownTags(
            List<Question> questions,
            TaxonomyVersion taxonomy,
            String declaredVersion,
            List<QuestionViolation> violations) {

        for (Question question : questions) {
            for (SubSkillCode tagged : question.taggedSubSkills()) {
                if (taxonomy.findSubSkill(tagged).isEmpty()) {
                    violations.add(
                            QuestionViolation.unknownTag(question.code(), tagged.value(), declaredVersion));
                }
            }
        }
    }

    public String bankVersion() {
        return bankVersion;
    }

    public String taxonomyVersion() {
        return taxonomyVersion;
    }

    public List<Question> questions() {
        return questions;
    }

    public int size() {
        return questions.size();
    }

    public boolean isEmpty() {
        return questions.isEmpty();
    }

    public Optional<Question> find(QuestionCode code) {
        return questions.stream().filter(question -> question.code().equals(code)).findFirst();
    }

    public List<Question> taggedWith(SubSkillCode subSkill) {
        return questions.stream().filter(question -> question.covers(subSkill)).toList();
    }

    public List<Question> atDifficulty(Difficulty difficulty) {
        return questions.stream()
                .filter(question -> question.difficulty().equals(difficulty))
                .toList();
    }

    private static void requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field, field + " is required on a question bank.");
        }
    }
}
