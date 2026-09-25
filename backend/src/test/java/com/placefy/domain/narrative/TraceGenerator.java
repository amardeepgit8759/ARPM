package com.placefy.domain.narrative;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Builds varied traces for the safety harness.
 *
 * <p>Seeded, so a harness failure is reproducible from its index alone rather than being a run
 * nobody can get back. The values are arbitrary test data — this generator is not a scoring
 * engine and the numbers it produces mean nothing beyond exercising the validator against
 * different shapes: empty traces, single-gap traces, wide traces, values at scale boundaries.
 */
final class TraceGenerator {

    private static final List<String[]> SUB_SKILLS = List.of(
            new String[] {"graph-traversal", "Graph Traversal (BFS and DFS)"},
            new String[] {"sql-joins", "Joins"},
            new String[] {"two-pointer-technique", "Two-Pointer Technique"},
            new String[] {"array-traversal", "Array Traversal"},
            new String[] {"hash-table-basics", "Hash Table Basics"},
            new String[] {"memoisation", "Memoisation"},
            new String[] {"dp-on-grids", "Dynamic Programming on Grids"},
            new String[] {"os-deadlock-conditions", "Deadlock Conditions"});

    private TraceGenerator() {}

    static SubSkillVocabulary vocabulary() {
        Map<String, SubSkillCode> terms = new LinkedHashMap<>();
        for (String[] entry : SUB_SKILLS) {
            SubSkillCode code = SubSkillCode.of(entry[0]);
            terms.put(entry[0], code);
            terms.put(entry[1], code);
        }
        return SubSkillVocabulary.of(terms);
    }

    /** {@code index} seeds the generator, so trace 47 is the same trace on every run. */
    static DecisionTrace generate(int index) {
        Random random = new Random(index);

        int gapCount = index % 5;
        int strengthCount = index % 3;

        List<TracedGap> gaps = new ArrayList<>();
        List<TracedStrength> strengths = new ArrayList<>();
        GapCriticality[] criticalities = GapCriticality.values();

        for (int i = 0; i < gapCount; i++) {
            String[] subSkill = SUB_SKILLS.get((index + i) % SUB_SKILLS.size());
            BigDecimal score = scale(random, index + i);
            gaps.add(TracedGap.of(
                    SubSkillCode.of(subSkill[0]),
                    subSkill[1],
                    score,
                    score.add(BigDecimal.valueOf(random.nextInt(30) + 1)),
                    criticalities[(index + i) % criticalities.length]));
        }

        for (int i = 0; i < strengthCount; i++) {
            String[] subSkill = SUB_SKILLS.get((index + gapCount + i) % SUB_SKILLS.size());
            strengths.add(TracedStrength.of(
                    SubSkillCode.of(subSkill[0]), subSkill[1], scale(random, index + gapCount + i)));
        }

        return DecisionTrace.of(
                UUID.nameUUIDFromBytes(("trace-" + index).getBytes()),
                "harness-engine",
                "harness-config",
                "2026.1-example",
                "0".repeat(64),
                TracedMeasure.of("Overall readiness", scale(random, index)),
                TracedMeasure.of("Target match", scale(random, index + 1), "%"),
                gaps,
                strengths,
                TracedMeasure.of("Evidence consistency", new BigDecimal(random.nextInt(100)).movePointLeft(2)));
    }

    /**
     * Mixes integers and two-decimal values so the harness covers both the plain case and the
     * scale-sensitive one, where {@code BigDecimal.equals} would disagree with {@code compareTo}.
     */
    private static BigDecimal scale(Random random, int index) {
        int whole = random.nextInt(100);
        return index % 3 == 0 ? BigDecimal.valueOf(whole) : BigDecimal.valueOf(whole).setScale(2);
    }
}
