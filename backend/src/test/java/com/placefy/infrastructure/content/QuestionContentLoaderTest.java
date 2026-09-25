package com.placefy.infrastructure.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.question.InvalidQuestionBankException;
import com.placefy.domain.question.QuestionBank;
import com.placefy.domain.question.QuestionViolation;
import com.placefy.domain.question.QuestionViolationType;
import com.placefy.domain.taxonomy.ContentChecksum;
import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.DomainCode;
import com.placefy.domain.taxonomy.EffortHours;
import com.placefy.domain.taxonomy.NodeName;
import com.placefy.domain.taxonomy.Skill;
import com.placefy.domain.taxonomy.SkillCode;
import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyVersionLabel;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every way a question file can be wrong, and the report an author gets for it.
 *
 * <p>Each fixture directory under {@code src/test/resources/questions-invalid} is a complete,
 * loadable bank except for the one mistake its name describes, so a failure here names the rule
 * that stopped working rather than just "something broke".
 */
class QuestionContentLoaderTest {

    private static final double THRESHOLD = 0.8;

    private final QuestionContentLoader loader = new QuestionContentLoader();

    /** Matches the taxonomy version the fixtures declare. */
    private static TaxonomyVersion fixtureTaxonomy() {
        return TaxonomyVersion.importedFrom(
                TaxonomyVersionId.of(UUID.fromString("00000000-0000-0000-0000-0000000000aa")),
                TaxonomyVersionLabel.of("test-taxonomy-1"),
                ContentChecksum.of("b".repeat(64)),
                Instant.parse("2026-09-22T09:00:00Z"),
                List.of(Domain.of(
                        DomainCode.of("arithmetic"),
                        NodeName.of("Arithmetic"),
                        List.of(Skill.of(
                                SkillCode.of("addition"),
                                NodeName.of("Addition"),
                                List.of(SubSkill.of(
                                        SubSkillCode.of("adding-integers"),
                                        NodeName.of("Adding Integers"),
                                        EffortHours.of(1),
                                        List.of())))))));
    }

    static Stream<Arguments> invalidFixtures() {
        return Stream.of(
                Arguments.of("missing-originality", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("missing-review", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("self-reviewed", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("not-original", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("not-approved", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("unknown-yaml-key", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("missing-difficulty", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("bad-correct-option", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("undeclared-bank-file", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("missing-declared-file", QuestionViolationType.MALFORMED_CONTENT),
                Arguments.of("unknown-tag", QuestionViolationType.UNKNOWN_SUB_SKILL_TAG),
                Arguments.of("duplicate-stem", QuestionViolationType.DUPLICATE_STEM));
    }

    @ParameterizedTest(name = "{0} is rejected as {1}")
    @MethodSource("invalidFixtures")
    void rejectsBrokenContent(String fixture, QuestionViolationType expected) {
        assertThatThrownBy(() -> load(fixturePath(fixture)))
                .isInstanceOf(InvalidQuestionBankException.class)
                .satisfies(e -> assertThat(((InvalidQuestionBankException) e).violations())
                        .as("violations reported for %s", fixture)
                        .extracting(QuestionViolation::type)
                        .contains(expected));
    }

    @Test
    @DisplayName("a valid fixture loads, so the invalid ones fail for their stated reason")
    void theControlFixtureLoads() {
        QuestionBank bank = load(fixturePath("valid-minimal"));

        assertThat(bank.size()).isEqualTo(1);
        assertThat(bank.bankVersion()).isEqualTo("fixture-1");
        assertThat(bank.taxonomyVersion()).isEqualTo("test-taxonomy-1");
    }

    @Test
    @DisplayName("a typo'd key names the key, because that is almost always what it is")
    void unknownKeyReportNamesTheKey() {
        assertThatThrownBy(() -> load(fixturePath("unknown-yaml-key"))).hasMessageContaining("explanaton");
    }

    @Test
    @DisplayName("self-review is refused with a reason an author can act on")
    void selfReviewReportIsLegible() {
        assertThatThrownBy(() -> load(fixturePath("self-reviewed")))
                .hasMessageContaining("Self-review is not review");
    }

    @Test
    @DisplayName("a question not attested as original is refused outright")
    void nonOriginalWorkIsRefused() {
        assertThatThrownBy(() -> load(fixturePath("not-original")))
                .hasMessageContaining("no approved route");
    }

    @Test
    @DisplayName("each broad MALFORMED_CONTENT fixture fails for the reason its name claims")
    void malformedFixturesFailForTheStatedReason() {
        // MALFORMED_CONTENT covers a lot of ground, so without this a fixture could fail for an
        // unrelated reason and still satisfy the type assertion above.
        assertThatThrownBy(() -> load(fixturePath("missing-difficulty"))).hasMessageContaining("difficulty");
        assertThatThrownBy(() -> load(fixturePath("bad-correct-option"))).hasMessageContaining("zz");
        assertThatThrownBy(() -> load(fixturePath("missing-originality"))).hasMessageContaining("originality");
        assertThatThrownBy(() -> load(fixturePath("missing-review"))).hasMessageContaining("review");
        assertThatThrownBy(() -> load(fixturePath("not-approved"))).hasMessageContaining("CHANGES_REQUESTED");
        assertThatThrownBy(() -> load(fixturePath("undeclared-bank-file"))).hasMessageContaining("orphan");
        assertThatThrownBy(() -> load(fixturePath("missing-declared-file"))).hasMessageContaining("ghost");
    }

    @Test
    void reportsAMissingManifestPlainly(@TempDir Path empty) {
        assertThatThrownBy(() -> load(empty))
                .isInstanceOf(InvalidQuestionBankException.class)
                .hasMessageContaining("Manifest not found");
    }

    private QuestionBank load(Path root) {
        return loader.load(root, fixtureTaxonomy(), THRESHOLD);
    }

    private static Path fixturePath(String name) {
        try {
            return Path.of(QuestionContentLoaderTest.class
                    .getClassLoader()
                    .getResource("questions-invalid/" + name)
                    .toURI());
        } catch (Exception e) {
            throw new IllegalStateException("Missing fixture: questions-invalid/" + name, e);
        }
    }
}
