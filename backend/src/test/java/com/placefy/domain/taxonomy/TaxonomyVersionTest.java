package com.placefy.domain.taxonomy;

import static com.placefy.domain.taxonomy.TaxonomyFixtures.CHECKSUM;
import static com.placefy.domain.taxonomy.TaxonomyFixtures.IMPORTED_AT;
import static com.placefy.domain.taxonomy.TaxonomyFixtures.domain;
import static com.placefy.domain.taxonomy.TaxonomyFixtures.minimal;
import static com.placefy.domain.taxonomy.TaxonomyFixtures.skill;
import static com.placefy.domain.taxonomy.TaxonomyFixtures.subSkill;
import static com.placefy.domain.taxonomy.TaxonomyFixtures.taxonomy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TaxonomyVersionTest {

    @Nested
    class Structure {

        @Test
        void acceptsTheSmallestValidTaxonomy() {
            TaxonomyVersion taxonomy = minimal();

            assertThat(taxonomy.subSkillCount()).isEqualTo(1);
            assertThat(taxonomy.status()).isEqualTo(TaxonomyStatus.DRAFT);
            assertThat(taxonomy.publishedAt()).isEmpty();
        }

        @Test
        @DisplayName("declaration order is preserved, because the validator report depends on it")
        void preservesDeclarationOrder() {
            TaxonomyVersion taxonomy = taxonomy(
                    domain("zebra", skill("z-skill", subSkill("z-one"), subSkill("z-two"))),
                    domain("alpha", skill("a-skill", subSkill("a-one"))));

            assertThat(taxonomy.domains()).extracting(d -> d.code().value()).containsExactly("zebra", "alpha");
            assertThat(taxonomy.allSubSkills())
                    .extracting(s -> s.code().value())
                    .containsExactly("z-one", "z-two", "a-one");
        }

        @Test
        void findsASubSkillByCodeAcrossDomains() {
            TaxonomyVersion taxonomy = taxonomy(
                    domain("dsa", skill("graphs", subSkill("bfs"))),
                    domain("dbms", skill("indexing", subSkill("b-tree-index"))));

            assertThat(taxonomy.findSubSkill(SubSkillCode.of("b-tree-index"))).isPresent();
            assertThat(taxonomy.findSubSkill(SubSkillCode.of("nope"))).isEmpty();
        }

        @Test
        void rejectsATaxonomyWithNoDomains() {
            assertThatThrownBy(TaxonomyFixtures::taxonomy)
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(e -> assertThat(((InvalidTaxonomyException) e)
                                    .hasViolationOfType(TaxonomyViolationType.EMPTY_TAXONOMY))
                            .isTrue());
        }

        @Test
        void rejectsADomainWithNoSkills() {
            assertThatThrownBy(() -> taxonomy(domain("dsa")))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.EMPTY_DOMAIN));
        }

        @Test
        void rejectsASkillWithNoSubSkills() {
            assertThatThrownBy(() -> taxonomy(domain("dsa", skill("arrays"))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.EMPTY_SKILL));
        }
    }

    @Nested
    class DuplicateCodes {

        @Test
        void rejectsDuplicateDomainCodes() {
            assertThatThrownBy(() -> taxonomy(
                            domain("dsa", skill("arrays", subSkill("one"))),
                            domain("dsa", skill("graphs", subSkill("two")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.DUPLICATE_DOMAIN_CODE));
        }

        @Test
        @DisplayName("skill codes are unique across the whole taxonomy, not per domain")
        void rejectsDuplicateSkillCodesAcrossDomains() {
            assertThatThrownBy(() -> taxonomy(
                            domain("dsa", skill("basics", subSkill("one"))),
                            domain("dbms", skill("basics", subSkill("two")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.DUPLICATE_SKILL_CODE));
        }

        @Test
        @DisplayName("sub-skill codes are unique globally, because prerequisites reference them globally")
        void rejectsDuplicateSubSkillCodesAcrossDomains() {
            assertThatThrownBy(() -> taxonomy(
                            domain("dsa", skill("arrays", subSkill("traversal"))),
                            domain("dbms", skill("indexing", subSkill("traversal")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.DUPLICATE_SUB_SKILL_CODE));
        }

        @Test
        @DisplayName("every duplicate is reported, not just the first")
        void reportsAllViolationsAtOnce() {
            assertThatThrownBy(() -> taxonomy(
                            domain("dsa", skill("arrays", subSkill("one"))),
                            domain("dsa", skill("arrays", subSkill("one")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(e -> {
                        List<TaxonomyViolation> violations = ((InvalidTaxonomyException) e).violations();
                        assertThat(violations)
                                .extracting(TaxonomyViolation::type)
                                .contains(
                                        TaxonomyViolationType.DUPLICATE_DOMAIN_CODE,
                                        TaxonomyViolationType.DUPLICATE_SKILL_CODE,
                                        TaxonomyViolationType.DUPLICATE_SUB_SKILL_CODE);
                    });
        }
    }

    @Nested
    class Prerequisites {

        @Test
        void acceptsAPrerequisiteThatCrossesDomains() {
            assertThatCode(() -> taxonomy(
                            domain("dsa", skill("recursion", subSkill("recursion-basics"))),
                            domain("advanced", skill("dp", subSkill("memoisation", "recursion-basics")))))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsAPrerequisiteThatDoesNotExist() {
            assertThatThrownBy(() ->
                            taxonomy(domain("dsa", skill("dp", subSkill("memoisation", "does-not-exist")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.UNKNOWN_PREREQUISITE))
                    .hasMessageContaining("does-not-exist");
        }

        @Test
        void rejectsASubSkillThatRequiresItself() {
            assertThatThrownBy(() -> taxonomy(domain("dsa", skill("dp", subSkill("memoisation", "memoisation")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.SELF_PREREQUISITE));
        }

        @Test
        void rejectsARepeatedPrerequisite() {
            assertThatThrownBy(() -> taxonomy(domain(
                            "dsa",
                            skill("core", subSkill("recursion-basics")),
                            skill("dp", subSkill("memoisation", "recursion-basics", "recursion-basics")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.DUPLICATE_PREREQUISITE));
        }

        @Test
        @DisplayName("an unknown reference is reported as unknown, not as a structural failure")
        void anUnknownReferenceDoesNotAlsoTripCycleDetection() {
            assertThatThrownBy(() -> taxonomy(domain("dsa", skill("dp", subSkill("memoisation", "ghost")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(e -> assertThat(((InvalidTaxonomyException) e).violations())
                            .extracting(TaxonomyViolation::type)
                            .containsExactly(TaxonomyViolationType.UNKNOWN_PREREQUISITE));
        }
    }

    @Nested
    class Cycles {

        @Test
        void rejectsATwoNodeCycle() {
            assertThatThrownBy(() -> taxonomy(domain(
                            "dsa", skill("core", subSkill("alpha", "beta"), subSkill("beta", "alpha")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.PREREQUISITE_CYCLE));
        }

        @Test
        void rejectsALongerCycle() {
            assertThatThrownBy(() -> taxonomy(domain(
                            "dsa",
                            skill(
                                    "core",
                                    subSkill("alpha", "gamma"),
                                    subSkill("beta", "alpha"),
                                    subSkill("gamma", "beta")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.PREREQUISITE_CYCLE));
        }

        @Test
        @DisplayName("a cycle spanning two domains is still a cycle")
        void rejectsACycleAcrossDomains() {
            assertThatThrownBy(() -> taxonomy(
                            domain("one", skill("s-one", subSkill("alpha", "beta"))),
                            domain("two", skill("s-two", subSkill("beta", "alpha")))))
                    .isInstanceOf(InvalidTaxonomyException.class)
                    .satisfies(hasType(TaxonomyViolationType.PREREQUISITE_CYCLE));
        }

        @Test
        @DisplayName("the report names the cycle path, so the author can find it")
        void namesTheCyclePath() {
            assertThatThrownBy(() -> taxonomy(domain(
                            "dsa", skill("core", subSkill("alpha", "beta"), subSkill("beta", "alpha")))))
                    .hasMessageContaining("alpha")
                    .hasMessageContaining("beta")
                    .hasMessageContaining("->");
        }

        @Test
        @DisplayName("a diamond is not a cycle")
        void acceptsADiamondShapedGraph() {
            assertThatCode(() -> taxonomy(domain(
                            "dsa",
                            skill(
                                    "core",
                                    subSkill("base"),
                                    subSkill("left", "base"),
                                    subSkill("right", "base"),
                                    subSkill("top", "left", "right")))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a long prerequisite chain is not a cycle")
        void acceptsADeepChain() {
            var chain = new java.util.ArrayList<SubSkill>();
            chain.add(subSkill("step-0"));
            for (int i = 1; i < 50; i++) {
                chain.add(subSkill("step-" + i, "step-" + (i - 1)));
            }
            Skill skill = Skill.of(SkillCode.of("chain"), NodeName.of("Chain"), chain);

            assertThatCode(() -> taxonomy(Domain.of(DomainCode.of("dsa"), NodeName.of("DSA"), List.of(skill))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Publishing {

        @Test
        void publishingMovesTheVersionIntoCirculation() {
            TaxonomyVersion published = minimal().publish(IMPORTED_AT.plus(Duration.ofMinutes(5)));

            assertThat(published.isPublished()).isTrue();
            assertThat(published.status()).isEqualTo(TaxonomyStatus.PUBLISHED);
            assertThat(published.publishedAt()).contains(IMPORTED_AT.plus(Duration.ofMinutes(5)));
        }

        @Test
        void publishingDoesNotMutateTheDraft() {
            TaxonomyVersion draft = minimal();
            draft.publish(IMPORTED_AT.plusSeconds(60));

            assertThat(draft.isPublished()).isFalse();
            assertThat(draft.publishedAt()).isEmpty();
        }

        @Test
        @DisplayName("re-publishing is refused rather than silently moving publishedAt")
        void refusesToPublishTwice() {
            TaxonomyVersion published = minimal().publish(IMPORTED_AT.plusSeconds(60));

            assertThatThrownBy(() -> published.publish(IMPORTED_AT.plusSeconds(120)))
                    .isInstanceOf(TaxonomyAlreadyPublishedException.class)
                    .hasMessageContaining("2026.1");
        }

        @Test
        void refusesToPublishBeforeImport() {
            assertThatThrownBy(() -> minimal().publish(IMPORTED_AT.minusSeconds(1)))
                    .isInstanceOf(com.placefy.domain.DomainValidationException.class);
        }

        @Test
        void identityIsTheIdAlone() {
            TaxonomyVersion draft = minimal();
            assertThat(draft.publish(IMPORTED_AT.plusSeconds(60))).isEqualTo(draft);
        }
    }

    @Nested
    class Determinism {

        @Test
        @DisplayName("the same content validated twice produces the same violations in the same order")
        void violationReportingIsStable() {
            List<TaxonomyViolation> first = violationsOf();
            List<TaxonomyViolation> second = violationsOf();

            assertThat(second).containsExactlyElementsOf(first);
        }

        @Test
        void buildingTheSameContentTwiceProducesEqualStructure() {
            assertThat(build().domains()).isEqualTo(build().domains());
        }

        private TaxonomyVersion build() {
            return TaxonomyVersion.importedFrom(
                    TaxonomyVersionId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    TaxonomyVersionLabel.of("2026.1"),
                    CHECKSUM,
                    IMPORTED_AT,
                    List.of(domain("dsa", skill("core", subSkill("base"), subSkill("next", "base")))));
        }

        private List<TaxonomyViolation> violationsOf() {
            try {
                taxonomy(domain(
                        "dsa",
                        skill("core", subSkill("alpha", "beta"), subSkill("beta", "alpha"), subSkill("ghost", "nope"))));
                throw new AssertionError("expected the taxonomy to be rejected");
            } catch (InvalidTaxonomyException e) {
                return e.violations();
            }
        }
    }

    private static java.util.function.Consumer<Throwable> hasType(TaxonomyViolationType type) {
        return e -> assertThat(((InvalidTaxonomyException) e).hasViolationOfType(type))
                .as("expected a %s violation, got %s", type, ((InvalidTaxonomyException) e).violations())
                .isTrue();
    }
}
