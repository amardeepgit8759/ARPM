package com.placefy.domain.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The claim this whole slice exists to support: no number a generator invents reaches a user.
 *
 * <p>100 traces, each put through ten hostile generators and one faithful one. Every hostile
 * output must be refused and every faithful one accepted — 1,100 checks, and a single escape
 * fails the build with the trace index and the text that got through.
 *
 * <p>This is the half of the exit criterion that can be met without a provider. The other half —
 * the same assertion against a live model — needs the real integration, and this harness is what
 * that integration will be pointed at.
 */
class NarrativeSafetyHarnessTest {

    private static final int TRACE_COUNT = 100;

    private final NarrativeValidator validator = new NarrativeValidator(TraceGenerator.vocabulary());
    private final NarrativeFormat format = DecisionTraceRenderer.format();

    @Test
    @DisplayName("across 100 traces, no hostile generation is ever accepted")
    void noHallucinatedNumberSurvives() {
        List<String> escapes = new ArrayList<>();

        for (int index = 0; index < TRACE_COUNT; index++) {
            DecisionTrace trace = TraceGenerator.generate(index);

            for (AdversarialProviders.Named hostile : AdversarialProviders.hostile()) {
                String generated = hostile.provider().apply(trace);
                NarrativeValidationResult result = validator.validate(generated, trace, format);

                if (result.isAccepted()) {
                    escapes.add("trace " + index + " / " + hostile.description());
                }
            }
        }

        assertThat(escapes)
                .as("hostile generations that were wrongly accepted")
                .isEmpty();
    }

    @Test
    @DisplayName("across the same 100 traces, the deterministic fallback is always accepted")
    void theFallbackNeverFails() {
        List<String> rejections = new ArrayList<>();

        for (int index = 0; index < TRACE_COUNT; index++) {
            DecisionTrace trace = TraceGenerator.generate(index);
            String rendered = AdversarialProviders.faithful().apply(trace);

            NarrativeValidationResult result = validator.validate(rendered, trace, format);
            if (result.isRejected()) {
                rejections.add("trace " + index + ": " + result.summary());
            }
        }

        // If this fails, the product has no safe output to fall back to when generation is off.
        assertThat(rejections).as("traces whose own fallback rendering was refused").isEmpty();
    }

    @Test
    @DisplayName("every hostile generator actually alters the text, so none passes vacuously")
    void everyHostileProviderReallyCorruptsSomething() {
        // A provider that silently returned the faithful rendering would be accepted every time
        // and look like a clean result. Asserting it differs is what stops the harness from
        // reporting safety it never tested.
        for (AdversarialProviders.Named hostile : AdversarialProviders.hostile()) {
            for (int index = 0; index < TRACE_COUNT; index++) {
                DecisionTrace trace = TraceGenerator.generate(index);

                assertThat(hostile.provider().apply(trace))
                        .as("'%s' left trace %d unchanged", hostile.description(), index)
                        .isNotEqualTo(AdversarialProviders.faithful().apply(trace));
            }
        }
    }

    @Test
    @DisplayName("the corruption each hostile generator makes is genuinely outside the trace")
    void hostileValuesAreChosenAgainstTheTraceUnderAttack() {
        for (int index = 0; index < TRACE_COUNT; index++) {
            DecisionTrace trace = TraceGenerator.generate(index);

            assertThat(trace.allowedNumbers().permits(AdversarialProviders.absentFrom(trace)))
                    .as("trace %d", index)
                    .isFalse();
            assertThat(trace.mentions(AdversarialProviders.unmeasuredSubSkill(trace)))
                    .as("trace %d", index)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the harness covers empty, small and wide traces")
    void theCorpusIsVaried() {
        List<Integer> gapCounts = new ArrayList<>();
        for (int index = 0; index < TRACE_COUNT; index++) {
            gapCounts.add(TraceGenerator.generate(index).topGaps().size());
        }

        assertThat(gapCounts).contains(0).contains(1).contains(4);
    }

    @Test
    @DisplayName("generation is reproducible, so a failure can be reopened by index")
    void tracesAreReproducible() {
        assertThat(TraceGenerator.generate(47)).isEqualTo(TraceGenerator.generate(47));
    }
}
