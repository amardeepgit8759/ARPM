# Placefy Readiness Scoring Specification

- **Status:** SKELETON — NOT YET SPECIFIED. Do not implement against this file until its
  status is `Accepted` and every `REPLACE_ME` is gone.
- **Owner:** _REPLACE_ME (who decides these rules)_
- **Spec version:** _REPLACE_ME (e.g. `1.0`) — this is what a scoring run records as
  `engine_version`_

---

## How to use this document

This skeleton was written by Claude at the user's request. **It deliberately contains no
formulas, no constants, no curves and no weights.** Every value the engine needs is a
product decision, and ADR-002 exists to keep those decisions out of a model's hands: a
readiness score has to be a rule somebody wrote down, or it is a guess wearing a
measurement's clothes.

Fill in every `REPLACE_ME`. Where a section asks for a formula, write the formula — not a
description of one. "Recent evidence counts for more" is not implementable; a half-life in
days is.

When this file is complete, the scoring engine is implementable as a pure function

```
score(inputs, config, taxonomy, clock) -> ScoringRun
```

with golden-file tests. Until then it is not, and no part of it should be built.

**A note on the golden files.** Section 12 asks for 20 fixture profiles. Their expected
outputs must be derived from this document by hand — not captured from whatever the
implementation first emits. A golden file generated from the code under test asserts only
that the code still does what it did, which is not the property we need.

---

## 1. Inputs

Everything the engine is allowed to read. Anything not listed here is not available to it.

| Input | Source | Notes |
|---|---|---|
| Self-ratings | Setup module, per skill | _REPLACE_ME: what scale? 1–5? Per skill or per sub-skill?_ |
| Assessment responses | Quick Check / assessments | Correct/incorrect per item, item difficulty, item sub-skill tags and tag weights |
| Taxonomy | `taxonomy_version` | Domains, skills, sub-skills, prerequisites, `default_effort_hours` |
| Requirements | `company_role_requirements` | Per-sub-skill threshold + criticality for the target company and role |
| Clock | `TimeProvider` port | Passed in, never read inside the engine |
| Config | `config_version` | Every constant in this document |

**Not inputs:** anything from an LLM, anything random, anything read from a clock inside the
computation, and anything a client sent that was not persisted first.

_REPLACE_ME: is there any other input? LeetCode/GitHub are explicitly out of this phase._

---

## 2. Evidence normalization

Different kinds of evidence have to land on one comparable scale before anything can be
combined.

- **Common evidence scale:** _REPLACE_ME (e.g. `[0.0, 1.0]`)_
- **Self-rating → evidence:** _REPLACE_ME — the exact mapping. If the rating scale is 1–5,
  give all five values. State whether the mapping is linear._
- **Assessment item → evidence:** _REPLACE_ME — how a correct and an incorrect response on
  an item of difficulty `d` become evidence. Does difficulty change the value of a correct
  answer, an incorrect one, or both?_
- **Partial credit:** _REPLACE_ME — does any item type produce a value other than 0 or 1?_
- **Unanswered / skipped items:** _REPLACE_ME — are they evidence of failure, or absent
  evidence? These are very different and the choice is visible to students._

### 2.1 Combining several pieces of evidence for one sub-skill

- **Rule:** _REPLACE_ME — weighted mean? Weighted mean with self-rating capped by assessed
  evidence? Something else?_
- **Relative weight of self-rating vs assessed evidence:** _REPLACE_ME_
- **Does assessed evidence override a self-rating when they disagree?** _REPLACE_ME — and
  if so, in both directions, or only when assessment is lower?_

---

## 3. Age decay

- **Applies to:** _REPLACE_ME (each piece of evidence individually? the aggregate?)_
- **Curve:** _REPLACE_ME — exponential with half-life `H` days, linear over `N` days, or
  step-wise. Give the function._
- **Parameter values:** _REPLACE_ME_
- **Floor:** _REPLACE_ME — does decayed evidence approach zero, or stop at a minimum?_
- **Reference instant:** the `clock` input. Decay is computed from evidence timestamp to
  that instant, so replaying a run with the same clock reproduces it exactly.

---

## 4. Confidence

Confidence describes how much evidence stands behind a score. It is reported separately from
the score.

- **Scale:** _REPLACE_ME (e.g. `[0.0, 1.0]`, or an ordinal `low | medium | high`)_
- **Determined by:** _REPLACE_ME — which of these, and how combined:_
  - _number of distinct pieces of evidence_
  - _recency of the newest evidence_
  - _spread of item difficulty_
  - _diversity of evidence kind (self-rating vs assessed)_
- **Formula:** _REPLACE_ME_
- **Does confidence modulate the score itself, or is it reported alongside only?**
  _REPLACE_ME._ This is a significant decision: if it modulates the score, a student with
  little evidence sees a lower number rather than a less certain one.

---

## 5. Aggregation and roll-up

### 5.1 Sub-skill score

- **Formula:** _REPLACE_ME_
- **Range:** _REPLACE_ME_
- **Sub-skill with no evidence at all:** _REPLACE_ME — null/absent, or a defined value? An
  absent score and a zero score must not be rendered the same way._

### 5.2 Item tag weights

- **Must an item's sub-skill tag weights sum to 1?** _REPLACE_ME_
- **If not, how are they normalized?** _REPLACE_ME_

### 5.3 Sub-skill → Skill

- **Weighting:** _REPLACE_ME — equal? by `default_effort_hours`? by requirement
  criticality?_
- **Skills containing sub-skills with no evidence:** _REPLACE_ME_

### 5.4 Skill → Domain

- **Weighting:** _REPLACE_ME_

### 5.5 Domain → Overall readiness

- **Weighting:** _REPLACE_ME — equal across domains, or weighted by the target role?_

### 5.6 Prerequisites

The taxonomy records prerequisite relationships between sub-skills.

- **Do prerequisites affect scoring at all?** _REPLACE_ME._ If yes, state exactly how — for
  example, whether a sub-skill's score is capped by its prerequisites'. If no, say so
  explicitly so the engine ignores the graph rather than someone assuming it should not.

---

## 6. Target match

- **`target_match_pct` definition:** _REPLACE_ME — the formula comparing the student's
  sub-skill scores against `company_role_requirements` for the target company and role._
- **Range and meaning:** _REPLACE_ME_
- **How criticality enters:** _REPLACE_ME — `blocking`, `important`, `nice_to_have` weights,
  or a rule where any unmet `blocking` requirement caps the result._
- **Requirements for sub-skills with no evidence:** _REPLACE_ME_
- **Requirement sets with `confidence: low`:** _REPLACE_ME — does a low-confidence
  requirement set change the result, change how it is presented, or neither?_

---

## 7. Preview Mode

A run enters Preview Mode when the evidence is too thin to call the result a score. **A score
in preview is never rendered as a score.**

There are **four** trigger conditions. Enumerate all four exactly:

1. _REPLACE_ME_
2. _REPLACE_ME_
3. _REPLACE_ME_
4. _REPLACE_ME_

- **Is preview per run, per domain, or both?** _REPLACE_ME_
- **What is persisted for a preview run?** _REPLACE_ME — is a numeric score still computed
  and stored while being withheld from display, or not computed at all? Storing it and
  hiding it is defensible, but it must be a deliberate choice, because anything persisted
  can later leak to a screen._
- **What does the student see instead?** _REPLACE_ME_
- **Exit condition:** _REPLACE_ME — what has to become true for the next run to leave
  preview._

---

## 8. Gap analysis

### 8.1 Deficit

- **Formula:** _REPLACE_ME (student sub-skill score vs requirement threshold)_
- **Sub-skills below threshold but with no evidence:** _REPLACE_ME_
- **Sub-skills with no requirement for the target role:** _REPLACE_ME — are they eligible to
  be gaps at all?_

### 8.2 Impact

- **Formula:** _REPLACE_ME_
- **Inputs:** _REPLACE_ME — criticality? how much overall readiness would move if closed?_

### 8.3 Cost

- **Formula:** _REPLACE_ME_
- **Is cost `default_effort_hours`, or derived from it?** _REPLACE_ME_
- **Do unmet prerequisites add their cost to a gap's cost?** _REPLACE_ME_
- **Does remaining prep time (weeks × weekly hours) affect cost or only feasibility?**
  _REPLACE_ME_

### 8.4 Priority and ordering

- **Priority formula:** _REPLACE_ME — how deficit, impact and cost combine._
- **Sort order:** _REPLACE_ME_
- **Tie-break chain:** _REPLACE_ME._ **This must produce a total order.** Two gaps with
  equal priority must have a defined, stable relative position — otherwise two runs over
  identical inputs can emit different rankings and the "deterministic" claim fails. End the
  chain with something guaranteed unique, such as the sub-skill code.
- **How many gaps are ranked?** _REPLACE_ME — all, or top N?_

### 8.5 Explainability

The exit criterion for this phase is that a student can explain from the screen alone why
their top gap is their top gap.

- **Fields persisted per ranked gap to support that:** _REPLACE_ME — at minimum the deficit,
  impact, cost and priority values, plus the evidence that produced the sub-skill score._

---

## 9. Precision and rounding

Golden files are meaningless without this section.

- **Internal arithmetic type:** _REPLACE_ME (`BigDecimal` with scale `N`? `double`?)_
  `double` makes cross-platform reproducibility harder to guarantee; `BigDecimal` with an
  explicit scale and rounding mode is the safer default for a system whose whole claim is
  reproducibility.
- **Rounding mode:** _REPLACE_ME (e.g. `HALF_UP`)_
- **Where rounding is applied:** _REPLACE_ME — at every intermediate step, or only on the
  persisted result? These give different numbers._
- **Decimal places persisted:** _REPLACE_ME, per field._
- **Display rounding:** _REPLACE_ME._ Must never differ from persisted rounding in a way
  that makes the screen disagree with the stored run.

---

## 10. Versioning

A scoring run persists `engine_version`, `config_version`, `taxonomy_version` and
`inputs_hash` (CLAUDE.md rule 7).

- **`engine_version` bumps when:** _REPLACE_ME — any change that could alter an output for
  identical inputs. List the categories._
- **`config_version` bumps when:** _REPLACE_ME_
- **`inputs_hash` covers exactly:** _REPLACE_ME — the canonical serialization of which
  inputs, in what order. Must be stable across JVM runs, so no hash-map iteration order._
- **Re-running an old run:** _REPLACE_ME — is the engine expected to reproduce a historical
  run byte-for-byte given its recorded versions, or only runs made under the current
  version? The first is a much stronger promise and needs old engine versions to remain
  executable._

---

## 11. Worked example

One complete calculation, by hand, from raw inputs to ranked gaps. This is the section that
catches ambiguity in every section above, and it is the one to write last.

_REPLACE_ME_

---

## 12. Golden fixture profiles

20 profiles committed under `backend/src/test/resources/golden/`. Each is an input profile
plus its expected output, derived from this document by hand.

Suggested coverage — adjust as you see fit:

| # | Profile | Exercises |
|---|---|---|
| 1 | No evidence at all | Preview trigger, absent vs zero |
| 2 | Self-ratings only | Preview trigger, self-rating mapping |
| 3 | Single assessment, all correct | Ceiling behaviour |
| 4 | Single assessment, all incorrect | Floor behaviour |
| 5 | Self-rating high, assessment low | Disagreement rule (§2.1) |
| 6 | Self-rating low, assessment high | Disagreement rule, other direction |
| 7 | Evidence at the decay half-life | Decay curve |
| 8 | Evidence far past the decay floor | Decay floor |
| 9 | Mixed-age evidence on one sub-skill | Decay + combination |
| 10 | One domain fully evidenced, others empty | Roll-up with gaps |
| 11 | All `blocking` requirements met | Target match ceiling |
| 12 | One `blocking` requirement unmet | Criticality rule |
| 13 | Only `nice_to_have` unmet | Criticality weighting |
| 14 | Two gaps with identical priority | **Tie-break determinism** |
| 15 | Gap whose prerequisite is also a gap | Prerequisite cost rule |
| 16 | Very short remaining prep time | Cost/feasibility |
| 17 | Very long remaining prep time | Cost/feasibility |
| 18 | Low-confidence requirement set | §6 confidence rule |
| 19 | Exactly at every threshold | Boundary inclusivity |
| 20 | Maximum evidence everywhere | Upper bound, no overflow |

Each fixture must also be run twice in the same test to assert byte-identical output, and
run with a shuffled input ordering to assert ordering independence.

---

## 13. Open questions

_REPLACE_ME — anything above you want to revisit before this is marked Accepted._
