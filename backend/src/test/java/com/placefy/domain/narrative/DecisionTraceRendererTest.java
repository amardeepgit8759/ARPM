package com.placefy.domain.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecisionTraceRendererTest {

    private final DecisionTraceRenderer renderer = new DecisionTraceRenderer();
    private final NarrativeValidator validator = new NarrativeValidator(TraceFixtures.vocabulary());

    @Test
    @DisplayName("the fallback's own output passes the validator")
    void rendererOutputIsAlwaysValid() {
        DecisionTrace trace = TraceFixtures.trace();

        NarrativeValidationResult result =
                validator.validate(renderer.render(trace), trace, DecisionTraceRenderer.format());

        assertThat(result.summary()).isEqualTo("accepted");
    }

    @Test
    @DisplayName("an empty trace still renders something valid, which is the whole point of a fallback")
    void anEmptyTraceStillRenders() {
        DecisionTrace trace = TraceFixtures.emptyTrace();
        String rendered = renderer.render(trace);

        assertThat(validator.validate(rendered, trace, DecisionTraceRenderer.format()).isAccepted())
                .isTrue();
        assertThat(rendered).contains("No gaps were ranked").contains("No strengths were identified");
    }

    @Test
    @DisplayName("an absent measurement is stated as absent, never drawn as a zero")
    void absenceIsNotRenderedAsZero() {
        String rendered = renderer.render(TraceFixtures.emptyTrace());

        assertThat(rendered).doesNotContain("0 gaps").doesNotContain("0 strengths");
    }

    @Test
    void printsEveryFigureExactlyAsTheTraceCarriesIt() {
        String rendered = renderer.render(TraceFixtures.trace());

        assertThat(rendered)
                .contains("62")
                .contains("48%")
                .contains("31")
                .contains("70")
                .contains("0.74");
    }

    @Test
    void namesEveryGapAndStrength() {
        String rendered = renderer.render(TraceFixtures.trace());

        assertThat(rendered)
                .contains("Graph Traversal (BFS and DFS)")
                .contains("Joins")
                .contains("Two-Pointer Technique")
                .contains("Array Traversal")
                .contains("Hash Table Basics");
    }

    @Test
    void distinguishesTheThreeCriticalityLevels() {
        String rendered = renderer.render(TraceFixtures.trace());

        assertThat(rendered)
                .contains("blocking for this role")
                .contains("important for this role")
                .contains("nice to have for this role");
    }

    @Test
    @DisplayName("rendering is deterministic")
    void theSameTraceAlwaysRendersIdentically() {
        DecisionTrace trace = TraceFixtures.trace();

        assertThat(renderer.render(trace)).isEqualTo(renderer.render(trace));
    }

    @Test
    @DisplayName("the renderer carries no arithmetic of its own")
    void rendersNoNumberTheTraceDoesNotHold() {
        DecisionTrace trace = TraceFixtures.trace();

        // Every number in the output must be one the trace permits. If the renderer ever computed
        // a difference or a percentage this fails, which is the guardrail on the guardrail.
        assertThat(NumberExtractor.extract(renderer.render(trace), TraceFixtures.vocabulary()))
                .allSatisfy(token -> assertThat(trace.allowedNumbers().permits(token.value()))
                        .as("rendered number %s", token.text())
                        .isTrue());
    }
}
