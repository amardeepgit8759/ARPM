package com.placefy.infrastructure.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.placefy.domain.taxonomy.Domain;
import com.placefy.domain.taxonomy.Skill;
import com.placefy.domain.taxonomy.SubSkill;
import com.placefy.domain.taxonomy.SubSkillCode;
import com.placefy.domain.taxonomy.TaxonomyVersion;
import com.placefy.domain.taxonomy.TaxonomyVersionId;
import com.placefy.support.RepositoryPaths;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The CI gate for taxonomy content.
 *
 * <p>This runs against the real files in {@code content/taxonomy} on every {@code ./gradlew
 * test}. There is no separate lint step to remember and no extra CI job: a malformed content
 * file fails the ordinary build, in the fast suite, without Docker.
 */
class TaxonomyContentValidationTest {

    private static final Instant AT = Instant.parse("2026-09-21T09:00:00Z");

    private final TaxonomyContentLoader loader = new TaxonomyContentLoader();

    @Test
    @DisplayName("the shipped taxonomy content is valid")
    void shippedContentLoads() {
        assertThatCode(this::load).doesNotThrowAnyException();
    }

    @Test
    void shippedContentDeclaresTheDomainsItShips() {
        TaxonomyVersion taxonomy = load();

        assertThat(taxonomy.domains())
                .extracting(domain -> domain.code().value())
                .containsExactly("data-structures-algorithms", "databases", "operating-systems");
    }

    @Test
    @DisplayName("every prerequisite in the shipped content resolves")
    void everyPrerequisiteResolves() {
        TaxonomyVersion taxonomy = load();

        for (SubSkill subSkill : taxonomy.allSubSkills()) {
            for (SubSkillCode prerequisite : subSkill.prerequisites()) {
                assertThat(taxonomy.findSubSkill(prerequisite))
                        .as("%s requires %s", subSkill.code(), prerequisite)
                        .isPresent();
            }
        }
    }

    @Test
    @DisplayName("the shipped content exercises a cross-domain prerequisite, since that is the hard case")
    void shippedContentIncludesACrossDomainPrerequisite() {
        TaxonomyVersion taxonomy = load();

        List<String> crossDomain = new ArrayList<>();
        for (Domain domain : taxonomy.domains()) {
            Set<SubSkillCode> ownCodes = new HashSet<>();
            domain.skills().stream().map(Skill::subSkills).flatMap(List::stream).forEach(s -> ownCodes.add(s.code()));

            for (Skill skill : domain.skills()) {
                for (SubSkill subSkill : skill.subSkills()) {
                    subSkill.prerequisites().stream()
                            .filter(prerequisite -> !ownCodes.contains(prerequisite))
                            .forEach(prerequisite ->
                                    crossDomain.add(subSkill.code() + " -> " + prerequisite));
                }
            }
        }

        assertThat(crossDomain).isNotEmpty();
    }

    @Test
    @DisplayName("underscore-prefixed files are templates and are not loaded as content")
    void templateFilesAreIgnored() {
        assertThat(RepositoryPaths.taxonomyContent().resolve("domains/_TEMPLATE.yaml"))
                .as("the template must exist, or the documented workflow for adding a domain is broken")
                .exists();

        // The template is full of REPLACE_ME values and would never validate. Loading succeeding
        // is the assertion that it was skipped.
        assertThatCode(this::load).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the checksum depends only on content, not on when or where it was loaded")
    void checksumIsStableAcrossLoads() {
        assertThat(load().sourceChecksum()).isEqualTo(load().sourceChecksum());
    }

    @Test
    @DisplayName("effort hours are present on every sub-skill, since the schema requires them")
    void everySubSkillCarriesEffortHours() {
        assertThat(load().allSubSkills()).allSatisfy(subSkill -> assertThat(
                        subSkill.defaultEffortHours().value())
                .isPositive());
    }

    private TaxonomyVersion load() {
        Path contentRoot = RepositoryPaths.taxonomyContent();
        return loader.load(contentRoot, TaxonomyVersionId.of(UUID.randomUUID()), AT);
    }
}
