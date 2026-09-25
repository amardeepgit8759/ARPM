package com.placefy.domain.narrative;

import com.placefy.domain.taxonomy.SubSkillCode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Stand-in generators for the safety harness.
 *
 * <p>Test-only on purpose. There is no {@code NarrativeProvider} port and no
 * {@code infrastructure/narrative} package yet — those arrive with the real integration. These
 * are functions from a trace to text, which is all the validator needs to be exercised against.
 *
 * <p>Each hostile provider corrupts a faithful rendering in one specific way a language model
 * plausibly would. The point is not that a model is malicious; every one of these is a thing
 * models do when asked to write fluently about numbers.
 *
 * <p><strong>Each corruption is computed against the trace it is attacking</strong>, rather than
 * using a fixed number. An earlier version hard-coded values like 77, which for some generated
 * traces happened to be a legitimate score — so the provider produced valid prose, the validator
 * correctly accepted it, and the harness looked like it had found an escape. A hostile provider
 * that is only sometimes hostile tests nothing reliably.
 */
final class AdversarialProviders {

    /** A generator: trace in, prose out. */
    interface Provider extends Function<DecisionTrace, String> {}

    private static final DecisionTraceRenderer RENDERER = new DecisionTraceRenderer();

    /** Spellable numbers, so the spelled-out attack can always find one the trace forbids. */
    private static final Map<Integer, String> SPELLABLE = new LinkedHashMap<>(Map.of(
            71, "seventy-one",
            83, "eighty-three",
            97, "ninety-seven",
            64, "sixty-four",
            38, "thirty-eight",
            52, "fifty-two"));

    private AdversarialProviders() {}

    static Provider faithful() {
        return RENDERER::render;
    }

    /** The smallest whole number this trace does not permit. */
    static BigDecimal absentFrom(DecisionTrace trace) {
        for (int candidate = 0; candidate <= 10_000; candidate++) {
            BigDecimal value = BigDecimal.valueOf(candidate);
            if (!trace.allowedNumbers().permits(value)) {
                return value;
            }
        }
        throw new IllegalStateException("No absent number found, which cannot happen for a finite trace.");
    }

    /** A sub-skill in the vocabulary that this run did not measure. */
    static SubSkillCode unmeasuredSubSkill(DecisionTrace trace) {
        for (String code : List.of(
                "memoisation",
                "dp-on-grids",
                "os-deadlock-conditions",
                "hash-table-basics",
                "array-traversal",
                "two-pointer-technique",
                "sql-joins",
                "graph-traversal")) {
            SubSkillCode candidate = SubSkillCode.of(code);
            if (!trace.mentions(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Every known sub-skill is in this trace; widen the vocabulary.");
    }

    /** Alters a figure in place — the most common and most plausible corruption. */
    static Provider nudgesANumber() {
        return trace -> RENDERER.render(trace) + System.lineSeparator() + "Overall you are at "
                + absentFrom(trace).toPlainString() + ".";
    }

    /** States a rounded version, the way a writer softens a figure. */
    static Provider roundsANumber() {
        return trace -> RENDERER.render(trace) + System.lineSeparator() + "In round terms, about "
                + absentFrom(trace).toPlainString() + " overall.";
    }

    /** Does the subtraction the trace deliberately withheld. */
    static Provider derivesArithmetic() {
        return trace -> RENDERER.render(trace) + System.lineSeparator() + "That leaves you "
                + absentFrom(trace).toPlainString() + " points short.";
    }

    /** Converts a score to a fraction, which is arithmetic dressed as formatting. */
    static Provider convertsUnits() {
        return trace -> RENDERER.render(trace) + System.lineSeparator() + "That is "
                + absentFrom(trace).add(BigDecimal.ONE).movePointLeft(3).toPlainString() + " of the way there.";
    }

    /** Spells an invented number out, evading any digits-only check. */
    static Provider spellsAnInventedNumber() {
        return trace -> {
            String spelled = SPELLABLE.entrySet().stream()
                    .filter(entry -> !trace.allowedNumbers().permits(BigDecimal.valueOf(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No spellable absent number available."));
            return RENDERER.render(trace) + System.lineSeparator() + "Overall you sit at " + spelled + ".";
        };
    }

    /** Names a real sub-skill this run never measured. */
    static Provider namesAnUnmeasuredSubSkill() {
        return trace -> RENDERER.render(trace) + System.lineSeparator() + unmeasuredSubSkill(trace).value()
                + " is also holding you back.";
    }

    /** Invents a section and fills it with advice nobody computed. */
    static Provider addsAnInventedSection() {
        return trace -> RENDERER.render(trace) + System.lineSeparator() + System.lineSeparator()
                + "## Next steps" + System.lineSeparator() + "Keep practising steadily.";
    }

    /** Drops a required section. */
    static Provider omitsASection() {
        return trace -> RENDERER.render(trace)
                .replace("## " + DecisionTraceRenderer.SECTION_CONSISTENCY, "## Consistency notes");
    }

    /** Runs far past the allowed length. */
    static Provider overruns() {
        return trace -> RENDERER.render(trace) + System.lineSeparator() + "padding ".repeat(2000);
    }

    /** Says nothing at all — a timed-out or filtered response. */
    static Provider producesNothing() {
        return trace -> "";
    }

    static List<Named> hostile() {
        return List.of(
                new Named("nudges a number", nudgesANumber()),
                new Named("rounds a number", roundsANumber()),
                new Named("derives arithmetic", derivesArithmetic()),
                new Named("converts units", convertsUnits()),
                new Named("spells an invented number", spellsAnInventedNumber()),
                new Named("names an unmeasured sub-skill", namesAnUnmeasuredSubSkill()),
                new Named("adds an invented section", addsAnInventedSection()),
                new Named("omits a section", omitsASection()),
                new Named("overruns the length", overruns()),
                new Named("produces nothing", producesNothing()));
    }

    record Named(String description, Provider provider) {}
}
