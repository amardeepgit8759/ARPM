package com.placefy.domain.narrative;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether generated prose may be shown to a student.
 *
 * <p>Pure and deterministic: the same trace and the same text always produce the same verdict.
 * It reads no clock, calls nothing over a network, and has no opinion about where the text came
 * from — a language model, a template, or a person pasting something in are all checked alike.
 *
 * <p>The governing bias is towards rejection. A false rejection costs a nicely-worded paragraph
 * and falls back to the deterministic renderer; a false acceptance puts a number on a student's
 * screen that no scoring run produced. Those are not comparable costs.
 */
public final class NarrativeValidator {

    private static final String SECTION_MARKER = "##";

    private final SubSkillVocabulary vocabulary;

    public NarrativeValidator(SubSkillVocabulary vocabulary) {
        this.vocabulary = Objects.requireNonNull(vocabulary, "vocabulary");
    }

    public NarrativeValidationResult validate(String narrative, DecisionTrace trace, NarrativeFormat format) {
        Objects.requireNonNull(trace, "trace");
        Objects.requireNonNull(format, "format");

        if (narrative == null || narrative.isBlank()) {
            return new NarrativeValidationResult(List.of(NarrativeViolation.empty()));
        }

        List<NarrativeViolation> violations = new ArrayList<>();
        checkLength(narrative, format, violations);
        checkSections(narrative, format, violations);
        checkSubSkills(narrative, trace, violations);
        checkNumbers(narrative, trace, violations);

        return new NarrativeValidationResult(violations);
    }

    private void checkLength(String narrative, NarrativeFormat format, List<NarrativeViolation> violations) {
        int length = narrative.strip().length();
        if (length < format.minCharacters()) {
            violations.add(NarrativeViolation.tooShort(length, format.minCharacters()));
        }
        if (length > format.maxCharacters()) {
            violations.add(NarrativeViolation.tooLong(length, format.maxCharacters()));
        }
    }

    private void checkSections(String narrative, NarrativeFormat format, List<NarrativeViolation> violations) {
        List<String> present = headingsIn(narrative);

        for (String required : format.requiredSections()) {
            if (!present.contains(required)) {
                violations.add(NarrativeViolation.missingSection(required));
            }
        }

        if (!format.allowExtraSections()) {
            for (String heading : present) {
                if (!format.requiredSections().contains(heading)) {
                    violations.add(NarrativeViolation.unexpectedSection(heading));
                }
            }
        }
    }

    private static List<String> headingsIn(String narrative) {
        List<String> headings = new ArrayList<>();
        for (String line : narrative.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith(SECTION_MARKER)) {
                headings.add(trimmed.replaceFirst("^#+", "").strip());
            }
        }
        return headings;
    }

    /**
     * Every sub-skill the text names has to be one this run actually measured. Naming a real
     * sub-skill from a different part of the taxonomy is the subtler failure: it reads as
     * authoritative and is about something the run never looked at.
     */
    private void checkSubSkills(String narrative, DecisionTrace trace, List<NarrativeViolation> violations) {
        for (SubSkillCode mentioned : vocabulary.mentionedIn(narrative)) {
            if (!trace.mentions(mentioned)) {
                violations.add(NarrativeViolation.subSkillNotInTrace(mentioned.value()));
            }
        }
    }

    private void checkNumbers(String narrative, DecisionTrace trace, List<NarrativeViolation> violations) {
        for (NumericToken token : NumberExtractor.extract(narrative, vocabulary)) {
            if (!trace.allowedNumbers().permits(token.value())) {
                violations.add(NarrativeViolation.unlistedNumber(token));
            }
        }
    }
}
