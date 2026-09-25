# ADR-006: No AI or ML anywhere in the current version

- **Status:** Accepted
- **Date:** 2026-09-23
- **Amends:** ADR-002, which assumed the Narrative Layer would phrase results with a generative
  model

## Context

ADR-002 separated a deterministic Decision Layer from a Narrative Layer, and expected the
Narrative Layer to use a language model whose output would be validated in code. The narrative
safety kernel (`domain/narrative`) was built for that: it rejects any text that states a number
the decision trace did not produce.

The product brief of 2026-09-23 tightened this. APRM's current version must not use artificial
intelligence, machine learning, large language models, generative AI or predictive models
anywhere: not for scoring, and not for explanations, recommendations, prioritisation or roadmap
generation. The brief describes the innovation as the explainable reassessment loop, not AI, and
keeps AI/ML as a documented future enhancement only.

## Decision

1. **Nothing in the current codebase calls a model.** No LLM SDK, no ML library, no inference
   runtime, and no "future AI" stub or placeholder interface.
2. **Explanations come from a rule-based Explanation Engine.** It fills predefined templates
   from a persisted `DecisionTrace`. It is deterministic like the rest of the Decision Layer:
   the same trace always produces the same text.
3. **The narrative validator is kept and repointed.** It now checks template output instead of
   model output. That is still worth doing: a template with a wrong placeholder can state the
   wrong number just as a model can, and the validator catches both.
4. **Enforced by a build rule.** `ArchitectureRulesTest.noAiOrMachineLearningLibraries` fails
   the build if any class depends on a known LLM-provider, orchestration or ML package. It
   covers the whole codebase, not only `domain` and `application`, because a model-written
   explanation is still a claim made to a student.
5. **Everything else in ADR-002 stands.** That includes the provenance rules: immutable runs
   with versions and an inputs hash, and no recomputation on read.

## Consequences

**Good.** Every sentence a student reads can be reproduced and audited from stored data. There
is no provider, API key, cost or latency to manage, and nothing to fail when an external service
is down.

**Costs.** Explanations will read like templates. Every explanation someone wants has to be
written as a template and the rule that selects it, which is slower than prompting and is the
point.

**Adding AI later** means a new ADR that supersedes this one. It has to say which layer the
model sits in and how its output is validated, and it has to remove packages from the build
rule's list deliberately rather than quietly.

## Alternatives considered

**Keep an LLM for phrasing only, behind the validator.** ADR-002's original design, and
technically sound. Rejected because the product owner excluded AI from this version outright.

**Leave an interface for a future model client.** Rejected: an unused port is scaffolding for
a phase that has not been decided. It would invite exactly the integration this ADR rules out.
