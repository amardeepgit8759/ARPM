# ADR-005: No scoring artifact exists before the specification does

- **Status:** Accepted
- **Date:** 2026-09-22

## Context

Placefy's central claim is that a readiness score is reproducible and can be checked by hand.
`docs/scoring-spec.md` is what makes that true, and it is a skeleton: 70 unresolved fields,
status `SKELETON`.

Three requests in quick succession would each have required inventing part of it: build the
engine "exactly as specified"; bump `engine_version` and update the affected golden files;
produce a full evidence trace to verify a score by hand. Every one was reasonable on its face.
None could be met without choosing a normalisation, a decay curve or a rounding rule that nobody
had decided.

The pressure is structural rather than accidental. Scoring sits under the narrative layer, the
dashboard, roadmap persistence and the trend chart, so an unfinished spec blocks most of the
product at once — and the cheapest-looking way out is always to proceed provisionally and fix it
later. What makes that particularly dangerous here is that fabricated scoring artifacts are
indistinguishable from real ones: a golden file full of invented expectations passes, and reads
to the next engineer as the specification.

## Decision

No scoring artifact is produced until `docs/scoring-spec.md` is `Accepted` with every field
resolved. Concretely:

- No formula, constant, curve, threshold or weight is chosen by an engineer or a model.
- No golden file is committed, and `engine_version` is not incremented.
- No readiness number is computed, persisted, rendered or traced.
- Golden expectations are derived by hand from the specification, never captured from the
  implementation.

Work that depends on scoring is **reordered around it, not stubbed**. Components that consume a
score are built against explicit inputs and tested without one — as the roadmap planner was,
taking a prioritised sub-skill list as a parameter, and as the narrative safety kernel was,
validating against a `DecisionTrace` supplied by its caller.

A request that cannot be met under this rule is reported as blocked, with the specific missing
fields named.

## Consequences

**Good.** No confidently wrong number reaches a student. The reproducibility claim stays true
rather than becoming aspirational. Blocked work is visible as blocked instead of appearing
complete. Substantial pieces — taxonomy, question pipeline, roadmap planner, safety kernel —
have shipped fully tested underneath the gap, so the spec is the only thing missing.

**Costs.** The critical path runs through a document, and progress looks slower than it is.
Several phases sit deliberately incomplete. The same refusal recurs, which is tiring and reads
as obstruction; it is the rule working, not failing.

**This constrains future work.** Any proposal that reaches a score through a provisional
constant is rejected on these grounds, however well-flagged the placeholder.

## Alternatives considered

**A provisional engine with placeholder constants, replaced once the spec lands.** Fastest
unblock. Rejected because placeholders survive: golden files generated against them encode the
guesses, every downstream test pins them, and a provisional score looks identical on screen to a
real one. "Temporary" numbers acquire dependents.

**Generating golden files from the implementation.** Standard practice elsewhere, and it would
have satisfied the request immediately. Rejected because such a file asserts only that behaviour
has not changed, never that it is correct — producing the most authoritative-looking artifact in
the repository while proving nothing.

**Letting a model propose the formulas for human review.** Rejected under ADR-002. Review does
not restore derivability: a reviewed guess is still not re-derivable from stored inputs, and the
version metadata on a run would record a process rather than a computation.

**Shipping scoring behind a feature flag.** Rejected because the flag protects users, not the
codebase. The constants still land, the goldens still encode them, and the flag is eventually
flipped by someone who was not party to this decision.
