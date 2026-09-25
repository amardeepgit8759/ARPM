package com.placefy.infrastructure.content;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.placefy.domain.DomainValidationException;
import com.placefy.domain.question.Difficulty;
import com.placefy.domain.question.InvalidQuestionBankException;
import com.placefy.domain.question.OriginalityAttestation;
import com.placefy.domain.question.Question;
import com.placefy.domain.question.QuestionBank;
import com.placefy.domain.question.QuestionCode;
import com.placefy.domain.question.QuestionOption;
import com.placefy.domain.question.QuestionType;
import com.placefy.domain.question.QuestionViolation;
import com.placefy.domain.question.ReviewOutcome;
import com.placefy.domain.question.ReviewerSignOff;
import com.placefy.domain.question.SubSkillTag;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Reads the question bank from YAML and hands back a validated {@link QuestionBank}.
 *
 * <p>Mirrors the taxonomy loader deliberately: a manifest naming its files, unknown keys refused,
 * every problem collected rather than the first thrown, and the whole thing exercised by a test
 * that runs on every build. An author should not have to learn two content pipelines.
 */
public final class QuestionContentLoader {

    public static final String MANIFEST_FILE_NAME = "questions.yaml";
    public static final String BANKS_DIRECTORY = "banks";

    private static final String YAML_SUFFIX = ".yaml";

    private final ObjectMapper yaml = JsonMapper.builder(new YAMLFactory())
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // A key nobody reads is almost always a typo for one that matters. "explanaton:" must
            // be an error, not a question that silently ships without an explanation.
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .addModule(new JavaTimeModule())
            .build();

    /**
     * @param contentRoot the {@code content/questions} directory
     * @param taxonomy the version tags are checked against
     * @param nearDuplicateThreshold phrasing overlap above which two stems count as duplicates
     * @throws InvalidQuestionBankException listing every problem found
     */
    public QuestionBank load(Path contentRoot, TaxonomyVersion taxonomy, double nearDuplicateThreshold) {
        List<QuestionViolation> violations = new ArrayList<>();

        Path manifestPath = contentRoot.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifestPath)) {
            throw new InvalidQuestionBankException(List.of(QuestionViolation.malformed(
                    MANIFEST_FILE_NAME, "Manifest not found at " + manifestPath.toAbsolutePath() + ".")));
        }

        QuestionManifestFile manifest = read(manifestPath, QuestionManifestFile.class, violations);
        if (manifest == null) {
            throw new InvalidQuestionBankException(violations);
        }

        List<String> declared = manifest.banks() == null ? List.of() : manifest.banks();
        Path banksDirectory = contentRoot.resolve(BANKS_DIRECTORY);
        reportUndeclaredFiles(banksDirectory, declared, violations);

        List<Question> questions = new ArrayList<>();
        for (String bankName : declared) {
            questions.addAll(readBank(banksDirectory, bankName, violations));
        }

        // Cross-question rules need a complete bank. Reporting "duplicate stem" against a file
        // that failed to parse would blame the wrong question.
        if (!violations.isEmpty()) {
            throw new InvalidQuestionBankException(violations);
        }

        return QuestionBank.of(
                manifest.bankVersion(), manifest.taxonomyVersion(), questions, taxonomy, nearDuplicateThreshold);
    }

    private void reportUndeclaredFiles(
            Path banksDirectory, List<String> declared, List<QuestionViolation> violations) {

        if (!Files.isDirectory(banksDirectory)) {
            // An empty bank is legitimate — the pipeline ships before the content does — so a
            // missing directory is only a problem when the manifest promised files in it.
            if (!declared.isEmpty()) {
                violations.add(QuestionViolation.malformed(
                        BANKS_DIRECTORY,
                        "The manifest declares " + declared.size() + " bank file(s) but "
                                + banksDirectory.toAbsolutePath() + " does not exist."));
            }
            return;
        }

        Set<String> declaredFileNames = new LinkedHashSet<>();
        declared.forEach(name -> declaredFileNames.add(name + YAML_SUFFIX));

        try (Stream<Path> files = Files.list(banksDirectory)) {
            files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(YAML_SUFFIX))
                    .filter(name -> !name.startsWith("_"))
                    .filter(name -> !declaredFileNames.contains(name))
                    .sorted()
                    .forEach(name -> violations.add(QuestionViolation.malformed(
                            BANKS_DIRECTORY + "/" + name,
                            "This file is not listed in " + MANIFEST_FILE_NAME + ", so its questions would "
                                    + "never be asked. Add it to the manifest, or remove the file.")));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + banksDirectory, e);
        }
    }

    private List<Question> readBank(Path banksDirectory, String bankName, List<QuestionViolation> violations) {
        String fileName = bankName + YAML_SUFFIX;
        Path path = banksDirectory.resolve(fileName);

        if (!Files.isRegularFile(path)) {
            violations.add(QuestionViolation.malformed(
                    BANKS_DIRECTORY + "/" + fileName,
                    "Declared in " + MANIFEST_FILE_NAME + " but no such file exists."));
            return List.of();
        }

        QuestionBankFile file = read(path, QuestionBankFile.class, violations);
        if (file == null || file.questions() == null) {
            return List.of();
        }

        List<Question> questions = new ArrayList<>();
        for (QuestionBankFile.QuestionFile questionFile : file.questions()) {
            Question question = buildQuestion(fileName, questionFile, violations);
            if (question != null) {
                questions.add(question);
            }
        }
        return questions;
    }

    private Question buildQuestion(
            String fileName, QuestionBankFile.QuestionFile file, List<QuestionViolation> violations) {

        String at = fileName + " / " + (file.code() == null ? "<no code>" : file.code());

        QuestionCode code = tryBuild(at, () -> QuestionCode.of(file.code()), violations);
        QuestionType type = tryBuild(at, () -> parseType(file.type()), violations);
        Difficulty difficulty = file.difficulty() == null
                ? reportMissing(at, "difficulty", violations)
                : tryBuild(at, () -> Difficulty.of(file.difficulty()), violations);

        List<QuestionOption> options = new ArrayList<>();
        for (QuestionBankFile.OptionFile option : orEmpty(file.options())) {
            QuestionOption built = tryBuild(at, () -> QuestionOption.of(option.id(), option.text()), violations);
            if (built != null) {
                options.add(built);
            }
        }

        List<SubSkillTag> tags = new ArrayList<>();
        for (QuestionBankFile.TagFile tag : orEmpty(file.tags())) {
            SubSkillTag built = tryBuild(
                    at, () -> SubSkillTag.of(SubSkillCode.of(tag.subSkill()), tag.weight()), violations);
            if (built != null) {
                tags.add(built);
            }
        }

        OriginalityAttestation originality = buildOriginality(at, file.originality(), violations);
        ReviewerSignOff review = buildReview(at, file.review(), violations);

        if (code == null || type == null || difficulty == null || originality == null || review == null) {
            return null;
        }

        return tryBuild(
                at,
                () -> new Question(
                        code,
                        type,
                        difficulty,
                        file.stem(),
                        options,
                        orEmpty(file.correctOptions()),
                        file.explanation(),
                        tags,
                        originality,
                        review),
                violations);
    }

    private OriginalityAttestation buildOriginality(
            String at, QuestionBankFile.OriginalityFile file, List<QuestionViolation> violations) {

        if (file == null) {
            violations.add(QuestionViolation.malformed(
                    at, "The 'originality' block is required: every question must be attested as original work."));
            return null;
        }
        if (file.originalWork() == null) {
            violations.add(QuestionViolation.malformed(at, "originality.original_work is required."));
            return null;
        }
        return tryBuild(
                at,
                () -> new OriginalityAttestation(
                        file.attestedBy(), file.attestedOn(), file.originalWork(), file.sourceNote()),
                violations);
    }

    private ReviewerSignOff buildReview(
            String at, QuestionBankFile.ReviewFile file, List<QuestionViolation> violations) {

        if (file == null) {
            violations.add(QuestionViolation.malformed(
                    at, "The 'review' block is required: every question needs a reviewer's sign-off."));
            return null;
        }
        return tryBuild(
                at,
                () -> new ReviewerSignOff(
                        file.reviewedBy(), file.reviewedOn(), parseOutcome(file.outcome()), file.comment()),
                violations);
    }

    private static QuestionType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("type", "Question type is required.");
        }
        try {
            return QuestionType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException(
                    "type", "Unknown question type '" + value + "'. Known types: SINGLE_CHOICE, MULTIPLE_CHOICE.");
        }
    }

    private static ReviewOutcome parseOutcome(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("outcome", "Review outcome is required.");
        }
        try {
            return ReviewOutcome.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException(
                    "outcome",
                    "Unknown review outcome '" + value + "'. Known outcomes: APPROVED, CHANGES_REQUESTED, "
                            + "REJECTED.");
        }
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> T reportMissing(String at, String field, List<QuestionViolation> violations) {
        violations.add(QuestionViolation.malformed(at, field + " is required."));
        return null;
    }

    private <T> T tryBuild(String location, Supplier<T> build, List<QuestionViolation> out) {
        try {
            return build.get();
        } catch (DomainValidationException e) {
            out.add(QuestionViolation.malformed(location, e.getMessage()));
            return null;
        }
    }

    private <T> T read(Path path, Class<T> type, List<QuestionViolation> violations) {
        try {
            return yaml.readValue(normalise(Files.readAllBytes(path)), type);
        } catch (IOException e) {
            violations.add(QuestionViolation.malformed(
                    path.getFileName().toString(), "Could not be read as YAML: " + rootMessage(e)));
            return null;
        }
    }

    private static byte[] normalise(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null) {
            return cause.getClass().getSimpleName();
        }
        int sourceMarker = message.indexOf(" at [Source:");
        return (sourceMarker > 0 ? message.substring(0, sourceMarker) : message).trim();
    }
}
