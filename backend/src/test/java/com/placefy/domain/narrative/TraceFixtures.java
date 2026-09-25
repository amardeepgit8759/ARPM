package com.placefy.domain.narrative;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traces for the kernel's tests.
 *
 * <p>The numbers here are arbitrary test data, not scoring output. Nothing in this kernel derives
 * them and no rule anywhere depends on them being these particular values — the scoring
 * specification that decides real figures is still unwritten, and none is invented here.
 */
final class TraceFixtures {

    static final UUID RUN_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private TraceFixtures() {}

    static DecisionTrace trace() {
        return DecisionTrace.of(
                RUN_ID,
                "test-engine",
                "test-config",
                "2026.1-example",
                "0".repeat(64),
                TracedMeasure.of("Overall readiness", new BigDecimal("62")),
                TracedMeasure.of("Target match", new BigDecimal("48"), "%"),
                List.of(
                        TracedGap.of(
                                SubSkillCode.of("graph-traversal"),
                                "Graph Traversal (BFS and DFS)",
                                new BigDecimal("31"),
                                new BigDecimal("70"),
                                GapCriticality.BLOCKING),
                        TracedGap.of(
                                SubSkillCode.of("sql-joins"),
                                "Joins",
                                new BigDecimal("55"),
                                new BigDecimal("65"),
                                GapCriticality.IMPORTANT),
                        TracedGap.of(
                                SubSkillCode.of("two-pointer-technique"),
                                "Two-Pointer Technique",
                                new BigDecimal("58"),
                                new BigDecimal("60"),
                                GapCriticality.NICE_TO_HAVE)),
                List.of(
                        TracedStrength.of(
                                SubSkillCode.of("array-traversal"), "Array Traversal", new BigDecimal("88")),
                        TracedStrength.of(
                                SubSkillCode.of("hash-table-basics"), "Hash Table Basics", new BigDecimal("81"))),
                TracedMeasure.of("Evidence consistency", new BigDecimal("0.74")));
    }

    static DecisionTrace emptyTrace() {
        return DecisionTrace.of(
                RUN_ID,
                "test-engine",
                "test-config",
                "2026.1-example",
                "0".repeat(64),
                TracedMeasure.of("Overall readiness", new BigDecimal("0")),
                TracedMeasure.of("Target match", new BigDecimal("0"), "%"),
                List.of(),
                List.of(),
                TracedMeasure.of("Evidence consistency", new BigDecimal("0")));
    }

    /**
     * Includes sub-skills that are NOT in the trace, which is what makes the "named a real
     * sub-skill from elsewhere in the taxonomy" test meaningful.
     */
    static SubSkillVocabulary vocabulary() {
        Map<String, SubSkillCode> terms = new LinkedHashMap<>();
        put(terms, "graph-traversal", "Graph Traversal (BFS and DFS)");
        put(terms, "sql-joins", "Joins");
        put(terms, "two-pointer-technique", "Two-Pointer Technique");
        put(terms, "array-traversal", "Array Traversal");
        put(terms, "hash-table-basics", "Hash Table Basics");
        put(terms, "memoisation", "Memoisation");
        put(terms, "dp-on-grids", "Dynamic Programming on Grids");
        put(terms, "os-deadlock-conditions", "Deadlock Conditions");
        return SubSkillVocabulary.of(terms);
    }

    private static void put(Map<String, SubSkillCode> terms, String code, String name) {
        SubSkillCode subSkillCode = SubSkillCode.of(code);
        terms.put(code, subSkillCode);
        terms.put(name, subSkillCode);
    }
}
