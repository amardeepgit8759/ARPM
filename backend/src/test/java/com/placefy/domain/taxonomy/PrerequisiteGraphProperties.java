package com.placefy.domain.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The structural guarantee the taxonomy makes to everything downstream: if it validated, the
 * prerequisite graph is a DAG, so a study order exists.
 *
 * <p>Generated rather than enumerated because the interesting failures are shapes nobody thinks
 * to write by hand — a back edge seven levels deep, a diamond that closes on itself.
 */
class PrerequisiteGraphProperties {

    @Property
    void anyAcceptedTaxonomyCanBeTopologicallyOrdered(@ForAll("acyclicSubSkills") List<SubSkill> subSkills) {
        TaxonomyVersion taxonomy = TaxonomyFixtures.taxonomy(
                Domain.of(
                        DomainCode.of("generated"),
                        NodeName.of("Generated"),
                        List.of(Skill.of(SkillCode.of("generated-skill"), NodeName.of("Generated"), subSkills))));

        assertThat(topologicalOrder(taxonomy)).hasSize(taxonomy.subSkillCount());
    }

    @Property
    void everyPrerequisiteResolvesToADeclaredSubSkill(@ForAll("acyclicSubSkills") List<SubSkill> subSkills) {
        TaxonomyVersion taxonomy = TaxonomyFixtures.taxonomy(
                Domain.of(
                        DomainCode.of("generated"),
                        NodeName.of("Generated"),
                        List.of(Skill.of(SkillCode.of("generated-skill"), NodeName.of("Generated"), subSkills))));

        for (SubSkill subSkill : taxonomy.allSubSkills()) {
            for (SubSkillCode prerequisite : subSkill.prerequisites()) {
                assertThat(taxonomy.findSubSkill(prerequisite))
                        .as("prerequisite %s of %s", prerequisite, subSkill.code())
                        .isPresent();
            }
        }
    }

    /**
     * Generates only backward references — node i may depend on any of 0..i-1 — which is acyclic
     * by construction. The property being checked is that the aggregate agrees.
     */
    @Provide
    Arbitrary<List<SubSkill>> acyclicSubSkills() {
        return Arbitraries.integers().between(1, 25).flatMap(size -> Arbitraries.randomValue(random -> {
            List<SubSkill> subSkills = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                List<SubSkillCode> prerequisites = new ArrayList<>();
                for (int candidate = 0; candidate < i; candidate++) {
                    if (random.nextInt(4) == 0) {
                        prerequisites.add(SubSkillCode.of("node-" + candidate));
                    }
                }
                subSkills.add(SubSkill.of(
                        SubSkillCode.of("node-" + i), NodeName.of("Node " + i), EffortHours.of(4), prerequisites));
            }
            return subSkills;
        }));
    }

    /** Kahn's algorithm. Returns fewer nodes than the taxonomy holds if a cycle slipped through. */
    private static List<SubSkillCode> topologicalOrder(TaxonomyVersion taxonomy) {
        List<SubSkillCode> ordered = new ArrayList<>();
        Set<SubSkillCode> placed = new HashSet<>();
        List<SubSkill> remaining = new ArrayList<>(taxonomy.allSubSkills());

        boolean progressed = true;
        while (progressed && !remaining.isEmpty()) {
            progressed = false;
            var iterator = remaining.iterator();
            while (iterator.hasNext()) {
                SubSkill candidate = iterator.next();
                if (placed.containsAll(candidate.prerequisites())) {
                    ordered.add(candidate.code());
                    placed.add(candidate.code());
                    iterator.remove();
                    progressed = true;
                }
            }
        }

        return ordered;
    }
}
