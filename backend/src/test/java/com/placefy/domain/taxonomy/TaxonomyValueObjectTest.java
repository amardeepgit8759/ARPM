package com.placefy.domain.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.placefy.domain.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TaxonomyValueObjectTest {

    @ParameterizedTest
    @ValueSource(strings = {"dsa", "data-structures", "a", "graph-traversal-bfs", "os2", "step-0"})
    void acceptsKebabCaseSlugs(String code) {
        assertThatCode(() -> SubSkillCode.of(code)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "  ",
                "Uppercase",
                "with space",
                "trailing-",
                "-leading",
                "double--hyphen",
                "under_score",
                "dot.separated",
                "slash/separated"
            })
    @DisplayName("anything that is not a lower-case kebab slug is rejected")
    void rejectsEverythingElse(String code) {
        assertThatThrownBy(() -> SubSkillCode.of(code)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void rejectsAnOverlongCode() {
        assertThatThrownBy(() -> SubSkillCode.of("a".repeat(65))).isInstanceOf(DomainValidationException.class);
    }

    @Test
    @DisplayName("the three code types validate identically but do not interchange")
    void codeTypesAreDistinct() {
        assertThat(DomainCode.of("dsa").value()).isEqualTo(SkillCode.of("dsa").value());
        // Distinct record types: a SkillCode cannot be passed where a SubSkillCode is expected,
        // which is the point of having three types rather than one String.
        assertThat((Object) DomainCode.of("dsa")).isNotEqualTo(SkillCode.of("dsa"));
    }

    @ParameterizedTest
    @ValueSource(ints = {EffortHours.MIN, 8, 40, EffortHours.MAX})
    void acceptsEffortWithinBounds(int hours) {
        assertThat(EffortHours.of(hours).value()).isEqualTo(hours);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, EffortHours.MAX + 1})
    void rejectsEffortOutsideBounds(int hours) {
        assertThatThrownBy(() -> EffortHours.of(hours))
                .isInstanceOf(DomainValidationException.class)
                .extracting(e -> ((DomainValidationException) e).field())
                .isEqualTo("defaultEffortHours");
    }

    @Test
    void nodeNameCollapsesWhitespace() {
        assertThat(NodeName.of("  Dynamic   Programming ").value()).isEqualTo("Dynamic Programming");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "x"})
    void rejectsBlankOrTooShortNames(String name) {
        assertThatThrownBy(() -> NodeName.of(name)).isInstanceOf(DomainValidationException.class);
    }

    @ParameterizedTest
    @MethodSource("validLabels")
    void acceptsVersionLabels(String label) {
        assertThat(TaxonomyVersionLabel.of(label).value()).isEqualTo(label);
    }

    static java.util.stream.Stream<String> validLabels() {
        return java.util.stream.Stream.of("2026.1", "v1", "2026-09-21", "1.0.0", "baseline_2026");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "-leading-hyphen", ".leading-dot", "has space", "has/slash"})
    void rejectsMalformedVersionLabels(String label) {
        assertThatThrownBy(() -> TaxonomyVersionLabel.of(label)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void checksumMustBeSixtyFourLowercaseHexCharacters() {
        assertThatCode(() -> ContentChecksum.of("0123456789abcdef".repeat(4))).doesNotThrowAnyException();
        assertThatThrownBy(() -> ContentChecksum.of("ABC")).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> ContentChecksum.of("A".repeat(64))).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void checksumToStringDoesNotPrintTheWholeDigest() {
        String digest = "0123456789abcdef".repeat(4);
        assertThat(ContentChecksum.of(digest).toString()).doesNotContain(digest).contains("0123456789ab");
    }

    @Test
    @DisplayName("a sub-skill with no prerequisites holds an empty list, never null")
    void prerequisitesDefaultToEmpty() {
        SubSkill subSkill = SubSkill.of(
                SubSkillCode.of("solo"), NodeName.of("Solo"), EffortHours.of(4), null);

        assertThat(subSkill.prerequisites()).isEmpty();
        assertThat(subSkill.hasPrerequisites()).isFalse();
    }
}
