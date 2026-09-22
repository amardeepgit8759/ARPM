package com.placefy.infrastructure.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.taxonomy.InvalidTaxonomyException;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.domain.taxonomy.TaxonomyViolation;
import com.placefy.domain.taxonomy.TaxonomyViolationType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
 * Every way a content file can be wrong, and the report the author gets for it.
 *
 * <p>The fixtures under {@code src/test/resources/taxonomy-invalid} are deliberately broken
 * content. Each directory is a complete, loadable taxonomy except for the single mistake its
 * name describes, so a failure here names the rule that stopped working.
 */
class TaxonomyContentLoaderTest {

    private static final Instant AT = Instant.parse("2026-09-21T09:00:00Z");

    private final TaxonomyContentLoader loader = new TaxonomyContentLoader();

    static Stream<Arguments> invalidFixtures() {
        return Stream.of(
                Arguments.of("duplicate-sub-skill-code", TaxonomyViolationType.DUPLICATE_SUB_SKILL_CODE),
                Arguments.of("duplicate-skill-code", TaxonomyViolationType.DUPLICATE_SKILL_CODE),
                Arguments.of("unknown-prerequisite", TaxonomyViolationType.UNKNOWN_PREREQUISITE),
                Arguments.of("prerequisite-cycle", TaxonomyViolationType.PREREQUISITE_CYCLE),
                Arguments.of("self-prerequisite", TaxonomyViolationType.SELF_PREREQUISITE),
                Arguments.of("unknown-yaml-key", TaxonomyViolationType.MALFORMED_CONTENT),
                Arguments.of("missing-effort-hours", TaxonomyViolationType.MALFORMED_CONTENT),
                Arguments.of("invalid-code-format", TaxonomyViolationType.MALFORMED_CONTENT),
                Arguments.of("undeclared-domain-file", TaxonomyViolationType.MALFORMED_CONTENT),
                Arguments.of("missing-declared-file", TaxonomyViolationType.MALFORMED_CONTENT),
                Arguments.of("filename-code-mismatch", TaxonomyViolationType.MALFORMED_CONTENT),
                Arguments.of("empty-skill", TaxonomyViolationType.MALFORMED_CONTENT));
    }

    @ParameterizedTest(name = "{0} is rejected as {1}")
    @MethodSource("invalidFixtures")
    void rejectsBrokenContent(String fixture, TaxonomyViolationType expected) {
        assertThatThrownBy(() -> load(fixturePath(fixture)))
                .isInstanceOf(InvalidTaxonomyException.class)
                .satisfies(e -> assertThat(((InvalidTaxonomyException) e).violations())
                        .as("violations reported for %s", fixture)
                        .extracting(TaxonomyViolation::type)
                        .contains(expected));
    }

    @Test
    @DisplayName("a valid fixture loads, so the invalid ones fail for their stated reason and not by accident")
    void theControlFixtureLoads() {
        TaxonomyVersion taxonomy = load(fixturePath("valid-minimal"));

        assertThat(taxonomy.label().value()).isEqualTo("fixture-1");
        assertThat(taxonomy.subSkillCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("the cycle report names the path so the author can find it")
    void cycleReportNamesThePath() {
        assertThatThrownBy(() -> load(fixturePath("prerequisite-cycle")))
                .hasMessageContaining("alpha-skill")
                .hasMessageContaining("beta-skill")
                .hasMessageContaining("->");
    }

    @Test
    @DisplayName("an unknown key names the key, because it is almost always a typo")
    void unknownKeyReportNamesTheKey() {
        assertThatThrownBy(() -> load(fixturePath("unknown-yaml-key")))
                .hasMessageContaining("prerequisits");
    }

    @Test
    @DisplayName("every parsing problem is reported at once, not one per run")
    void reportsEveryParsingProblemInOnePass() {
        assertThatThrownBy(() -> load(fixturePath("many-problems")))
                .isInstanceOf(InvalidTaxonomyException.class)
                .satisfies(e -> assertThat(((InvalidTaxonomyException) e).violations())
                        .extracting(TaxonomyViolation::location)
                        .hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    @DisplayName("every structural problem is reported at once too")
    void reportsEveryStructuralProblemInOnePass() {
        assertThatThrownBy(() -> load(fixturePath("many-structural-problems")))
                .isInstanceOf(InvalidTaxonomyException.class)
                .satisfies(e -> assertThat(((InvalidTaxonomyException) e).violations())
                        .extracting(TaxonomyViolation::type)
                        .contains(
                                TaxonomyViolationType.DUPLICATE_SUB_SKILL_CODE,
                                TaxonomyViolationType.UNKNOWN_PREREQUISITE));
    }

    @Test
    @DisplayName("validation runs in two stages, and parsing failures suppress structural ones")
    void parsingFailuresAreReportedBeforeStructuralOnes() {
        // Deliberate: a file that failed to parse contributes no sub-skills, so every reference
        // into it would be reported as an unknown prerequisite. That noise would bury the one
        // error that actually matters, so the loader stops after the parsing stage.
        assertThatThrownBy(() -> load(fixturePath("many-problems")))
                .satisfies(e -> assertThat(((InvalidTaxonomyException) e).violations())
                        .extracting(TaxonomyViolation::type)
                        .containsOnly(TaxonomyViolationType.MALFORMED_CONTENT));
    }

    @Test
    void reportsAMissingManifestPlainly(@TempDir Path empty) {
        assertThatThrownBy(() -> load(empty))
                .isInstanceOf(InvalidTaxonomyException.class)
                .hasMessageContaining("Manifest not found");
    }

    @Test
    @DisplayName("line endings do not change the checksum, so Windows and Linux agree")
    void checksumIgnoresLineEndings(@TempDir Path workspace) throws IOException {
        Path unix = workspace.resolve("unix");
        Path windows = workspace.resolve("windows");
        copyWithLineEndings(fixturePath("valid-minimal"), unix, "\n");
        copyWithLineEndings(fixturePath("valid-minimal"), windows, "\r\n");

        assertThat(load(windows).sourceChecksum()).isEqualTo(load(unix).sourceChecksum());
    }

    @Test
    @DisplayName("editing content changes the checksum, which is what catches an unbumped version")
    void checksumChangesWithContent() {
        assertThat(load(fixturePath("valid-minimal")).sourceChecksum())
                .isNotEqualTo(load(fixturePath("valid-minimal-edited")).sourceChecksum());
    }

    private TaxonomyVersion load(Path root) {
        return loader.load(root, TaxonomyVersionId.of(UUID.randomUUID()), AT);
    }

    private static Path fixturePath(String name) {
        try {
            return Path.of(TaxonomyContentLoaderTest.class
                    .getClassLoader()
                    .getResource("taxonomy-invalid/" + name)
                    .toURI());
        } catch (Exception e) {
            throw new IllegalStateException("Missing fixture: taxonomy-invalid/" + name, e);
        }
    }

    private static void copyWithLineEndings(Path source, Path target, String lineEnding) throws IOException {
        try (var paths = Files.walk(source)) {
            paths.forEach(path -> {
                try {
                    Path destination = target.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        String normalised = Files.readString(path).replace("\r\n", "\n");
                        Files.writeString(destination, normalised.replace("\n", lineEnding));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        assertThat(List.of(target)).isNotEmpty();
    }
}
