# ADR-002: The Decision Layer computes; the Narrative Layer only phrases

- **Status:** Accepted
- **Date:** 2026-09-15

## Context

Placefy tells a student how ready they are for a specific role at a specific company, ranks the
gaps between them and that company's hiring bar, and dates a plan to close them. Students will
make real decisions on those numbers — what to study, which companies to apply to, whether they
are ready this cycle or the next.

Generative models are good at phrasing such results and unsuitable for producing them. They are
not reproducible, they cannot be audited after the fact, and they will produce a confident,
plausible number on request. A readiness score that changes between two identical assessments,
or that cannot be explained six months later when a student asks why it said 62, is not a
measurement. It is a guess wearing a measurement's clothes.

The separation is easy to state and easy to lose. It gets lost one reasonable-looking commit at
a time: a model asked to "summarise and score", a prompt that returns a suggested weighting, a
narrative that quietly recalculates a percentage to make a sentence read better.

## Decision

Two layers with an absolute boundary.

**The Decision Layer** computes every score, every gap, every roadmap date. Rule-based and
deterministic. Pure functions of stored inputs. No ML, no LLM, no randomness, no clock reads
inside the logic — time and identifiers arrive through ports. Same input, same output, forever.

**The Narrative Layer** phrases results the Decision Layer has already computed. It receives a
finished result and returns prose. It cannot produce a number, alter a gap, reorder a ranking,
or influence any input to a computation.

Three mechanisms hold the boundary, in order of strength:

1. **Structure.** The Decision Layer lives in `domain` and `application`, which have no
   framework and no network client on their classpath. There is nothing there to call a model
   with.
2. **Output validation.** Narrative output is validated in code before it is used or stored —
   every figure it contains must match a value from the computed result it was given. Enforced
   by a validator, not by instructing the model to behave. A prompt is a request; a validator is
   a guarantee.
3. **Provenance.** Every computed result is persisted as an immutable run carrying
   `engine_version`, `config_version`, `taxonomy_version` and `inputs_hash`. Read endpoints are
   projections of those runs. Nothing recomputes on read, so nothing on screen can be a number
   that was never stored.

An ArchUnit rule already fails the build if anything in `domain` or `application` reads a clock
or a random source. When a model client is introduced it will be added to that rule's
prohibited list in the same commit.

## Consequences

**Good.** Any score can be recomputed from its recorded inputs and versions and must come out
identical — that is a test, not an aspiration. A support question about a specific number has a
factual answer. The narrative can be regenerated, reworded or switched to a different model
without any number moving.

**Costs.** Everything the product asserts must be expressible as a rule somebody wrote down.
There is no path where a model's judgement fills a gap in the taxonomy or the thresholds.
Content and thresholds must be sourced and reviewed rather than generated, which is slower and
is the point. The narrative validator is real work, and it will reject output that reads well.

**This constrains future features.** Any proposal where a model's output reaches a number is
rejected on these grounds regardless of how much it would improve the product's feel.

## Alternatives considered

**Model-assisted scoring with human review.** More adaptive, and defensible in some products.
Rejected because review does not restore reproducibility: a reviewed guess is still not
re-derivable from stored inputs, and the version metadata on a run would be recording a process
rather than a computation.

**Prompt-constrained generation** — instructing the model to return only values it was given.
Rejected because it is unenforceable. It fails silently and rarely, which is the worst failure
profile: it works in every test and breaks for one student.

**A single layer that both computes and narrates.** Fewer moving parts. Rejected because it
makes the boundary invisible, and an invisible boundary is one nobody can be held to.
