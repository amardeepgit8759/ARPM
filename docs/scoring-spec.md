# APRM Readiness Scoring Specification

- **Status:** DRAFT 1.0: decisions recorded 2026-09-24, **not yet Accepted**. The open
  questions in §13 must be answered first. Do not implement against this file (ADR-005) until
  the owner changes this line to `Accepted`.
- **Owner:** APRM Project Team / System Owner
- **Spec version:** `1.0`. A scoring run records this as `engine_version`.

---

## How to use this document

Every rule here was decided by the owner. None was chosen by an engineer or a model (ADR-002,
ADR-005, ADR-006). Where a rule was not decided, the text says **OPEN** and points to §13,
rather than filling the gap with a plausible guess.

When the file is Accepted, the scoring engine is a pure function

```text
score(inputs, requirementVersion, taxonomyVersion, clock) -> ScoringRun
```

with golden-file tests. Expected outputs for those tests are derived from this document by hand,
never captured from the implementation.

**No AI, ML, LLM or generative model takes part in any step below** (ADR-006). Every value a
run produces is traceable to its inputs, the rule that was applied, and the requirement,
taxonomy and engine versions in force.

---

## 1. Inputs

Everything the engine may read. Anything not listed is not available to it.

| Input | Source | Notes |
|---|---|---|
| Self-ratings | Student self-assessment | Scale and level: **OPEN**, §13 Q2 |
| Assessment responses | Every stored assessment attempt | Per question: correct, incorrect or skipped; the question's sub-skill tags; attempt timestamp |
| Taxonomy | `taxonomy_version` | Domains, skills, sub-skills, `default_effort_hours` |
| Requirement version | The student's target company-role | Per requirement: required %, criticality (HIGH/MEDIUM/LOW); per domain: weight % |
| Clock | `TimeProvider` port | Stamps the run; never read inside the calculation |

**Not inputs in 1.0:**

- LeetCode and GitHub activity. It is stored and shown as evidence in the competency profile,
  but it never contributes to a score.
- Anything from an AI or ML model, anything random, and anything a client sent without it
  being persisted first.

---

## 2. Evidence

All scores are on one scale: **percent, 0.0 to 100.0**.

### 2.1 Questions

- A correct answer scores **100**. An incorrect answer scores **0**.
- A skipped question counts as incorrect: **0**.
- **Every question has equal weight.** Difficulty does not change a question's value.
- There is no partial credit.
- Questions tagged to more than one sub-skill: **OPEN**, §13 Q5.

### 2.2 Self-ratings

- **A self-rating is used only until a valid test result exists for that sub-skill.** Once one
  exists, the test result replaces the self-rating entirely. They are never blended.
- The conversion from rating to percent, and the level at which it is recorded: **OPEN**, §13 Q2.
- How this rule interacts with the minimum-evidence rule (§2.4): **OPEN**, §13 Q1.

### 2.3 Repeat attempts

- **Current readiness uses the latest valid attempt only.** Earlier attempts are not averaged
  in and do not decay. They stay stored, unchanged, for progress history.
- Whether "latest" is decided per sub-skill or across the whole assessment: **OPEN**, §13 Q4.

### 2.4 Minimum evidence

- A sub-skill needs **at least 2 answered questions** in the attempt being used before it gets a
  numerical score.
- With fewer than 2, the sub-skill is **Not enough evidence**. No number is calculated or stored
  for it, and the screen shows the words "Not enough evidence", never a zero.
- Whether skipped questions (which score 0) count towards the 2: **OPEN**, §13 Q3.

---

## 3. Age decay

**None.** Rule §2.3 (the latest valid attempt only) replaces decay. Evidence does not lose weight
with time.

---

## 4. Confidence

**No separate confidence measure in 1.0.** The minimum-evidence rule (§2.4) is the only rule
about how much evidence stands behind a score: a sub-skill either has a score or is "Not enough
evidence".

---

## 5. Aggregation

### 5.1 Sub-skill score

For sub-skill `s`, with `Q(s)` the counted questions tagged `s` in the attempt being used:

```text
SubSkill(s) = ( Σ score(q) for q in Q(s) ) / |Q(s)|        when |Q(s)| ≥ 2
            = Not enough evidence                          otherwise
```

Range 0.0–100.0. The self-rating fallback is subject to §13 Q1.

### 5.2 Sub-skill → Skill → Domain: effort-weighted averages

Each sub-skill's influence is proportional to its configured `default_effort_hours` `e(s)`:

```text
Skill(k)  = Σ e(s) × SubSkill(s) / Σ e(s)     over sub-skills s in k
Domain(d) = Σ E(k) × Skill(k)    / Σ E(k)     over skills k in d,   E(k) = Σ e(s) for s in k
```

A domain is therefore the effort-weighted average of all its sub-skills. What happens when some
sub-skills are "Not enough evidence": **OPEN**, §13 Q6.

### 5.3 Domain → Overall readiness: weighted by target role

```text
Overall = Σ w(d) × Domain(d) / 100     over domains d in the requirement version
```

- The weights `w(d)` are configured per company-role **as part of the requirement version**.
  There is no global set of domain weights.
- The weights of one requirement version must **sum to exactly 100**. The admin tools refuse to
  save any other total.
- A domain with no weight in the requirement version does not affect overall readiness.
- Domains with no score: **OPEN**, §13 Q6.

### 5.4 Readiness bands

The labels shown next to a score (for example Strong, Moderate, Needs improvement) and their
boundaries: **OPEN**, §13 Q12. Wherever they land, they are versioned configuration, not code.

### 5.5 Prerequisites

Whether prerequisites affect scores: **OPEN**, §13 Q11. They do decide roadmap order.

---

## 6. Requirements and target match

### 6.1 Requirements

- For each requirement, the admin enters an **exact required percentage** and a **criticality**
  (HIGH, MEDIUM or LOW).
- **Criticality never sets or changes the required percentage.** It affects prioritisation only
  (§8.3).
- A required percentage must be greater than 0 and at most 100. Zero would make the target-match
  ratio undefined.
- Requirements belong to a **version**. A run records the requirement version it used, so an old
  run is always explained against the requirements that applied when it was made.
- The level at which a requirement is set (sub-skill, skill or domain): **OPEN**, §13 Q7.

### 6.2 Target match

```text
ratio(r)    = min( StudentScore(r) / Required(r), 1 )
TargetMatch = 100 × average of ratio(r) over the applicable requirements r
```

- Each ratio is capped at 1, so exceeding one requirement cannot make up for missing another.
- The result is 0.0–100.0 and can never exceed 100.
- Criticality does not weight target match.
- Requirements whose score is "Not enough evidence": **OPEN**, §13 Q8.

---

## 7. Not enough evidence (replaces Preview Mode)

There is no run-level Preview Mode in 1.0. Thin evidence is handled per sub-skill by §2.4:

- **Persisted:** the status "Not enough evidence" and the count of counted questions. No number
  is calculated, so none can leak to a screen.
- **Shown to the student:** "Not enough evidence", with how many more answered questions are
  needed.
- **Exit:** a later attempt meeting §2.4 for that sub-skill.

How missing sub-skills affect skill, domain, overall and target match is §13 Q6 and Q8.

---

## 8. Gap analysis and prioritisation

### 8.1 Gap

For each requirement `r` whose student score exists:

```text
Gap(r) = max( 0, Required(r) − StudentScore(r) )
```

- A gap of **0** has the status **Requirement met**. It is not a LOW priority.
- A sub-skill with no requirement for the target role is never a gap.

### 8.2 Priority bands

| Gap | Priority |
| --- | --- |
| greater than 25 | **HIGH**, whatever the criticality |
| 10 to 25 | **MEDIUM** |
| greater than 0 and below 10 | **LOW** |
| 0 | **Requirement met** (not ranked) |

Whether exactly 10 and exactly 25 count as MEDIUM: **OPEN**, §13 Q9.

### 8.3 Ordering

Gaps are ranked by priority band (HIGH, then MEDIUM, then LOW). Within one band, ties are broken
in this order:

1. Larger gap first.
2. Higher criticality first (HIGH, then MEDIUM, then LOW).
3. Fewer estimated effort hours first.
4. Alphabetical order of the sub-skill name, then its code. The code is unique, so the order is
   total: two runs over the same inputs always produce the same ranking.

All gaps are ranked; the ranking is not cut to a top N. Whether criticality plays any part
beyond the tie-break: **OPEN**, §13 Q9.

### 8.4 What is stored per ranked gap

The required %, student score, gap, priority band, criticality, effort hours, rank, and the
questions and attempt that produced the student score. That is enough to explain from the screen
alone why the top gap is the top gap.

---

## 9. Precision and rounding

- **Stored and displayed values have one decimal place** (for example 62.5).
- Internal arithmetic uses `BigDecimal`, never `double`, so results are identical on every
  platform.
- Rounding mode, and whether gaps and ratios are computed from rounded or unrounded scores:
  **OPEN**, §13 Q10.

---

## 10. Versioning and traceability

Every run is stored immutably with:

- `engine_version`: this spec's version (`1.0`).
- `requirement_version`: the company-role requirement version used, including domain weights,
  required percentages and criticalities.
- `taxonomy_version`: which sub-skills, skills, domains and effort hours were used.
- `inputs_hash`: SHA-256 of a canonical serialisation of every input above. Collections are sorted
  by code, so the hash never depends on hash-map order.
- A **decision trace**: every input value, every rule applied, every weight and threshold,
  every intermediate result, and every final score, gap and priority.

**When versions change:**

- `engine_version` changes whenever a rule in this document changes.
- `requirement_version` changes whenever an admin publishes changed requirements.
- `taxonomy_version` changes whenever the taxonomy is republished.

**Old runs** are never recalculated or edited. They are read back from storage together with
their trace. The engine must reproduce any run made under the current `engine_version` exactly
from its recorded inputs.

---

## 11. Worked example

To be written once §13 is answered. A worked example calculated by hand before then would have
to guess at exactly the rules still open.

---

## 12. Golden fixture profiles

Committed under `backend/src/test/resources/golden/`, with expected outputs derived by hand from
this document once it is Accepted.

| # | Profile | Exercises |
|---|---|---|
| 1 | No evidence at all | Not enough evidence everywhere, absent vs zero |
| 2 | Self-ratings only | Self-rating rule (§13 Q1) |
| 3 | One answered question on a sub-skill | Minimum-evidence boundary |
| 4 | Exactly 2 answered questions | Minimum-evidence boundary, other side |
| 5 | Skipped questions | Skipped scores 0; counting (§13 Q3) |
| 6 | All correct | Ceiling: 100.0, target match capped at 100 |
| 7 | All incorrect | Floor: 0.0 |
| 8 | Self-rating then a valid test | Test replaces self-rating |
| 9 | Two attempts, second worse | Latest attempt used, first kept in history |
| 10 | Sub-skills with unequal effort hours | Effort-weighted roll-up |
| 11 | Same scores, two company-roles with different domain weights | Role-specific overall |
| 12 | A domain with no weight | Excluded from overall |
| 13 | Score above one requirement, below another | Ratio capping in target match |
| 14 | Gap of exactly 0 | Requirement met, not LOW |
| 15 | Gaps of exactly 10 and 25 | Band boundaries (§13 Q9) |
| 16 | Gap over 25 on a LOW-criticality requirement | HIGH regardless of criticality |
| 17 | Two gaps equal in band, size and criticality | Effort-hours tie-break |
| 18 | Equal on every key but name | Alphabetical tie-break, total order |
| 19 | Values needing rounding at .x5 | Rounding mode (§13 Q10) |
| 20 | Some sub-skills without evidence in a domain | Partial roll-up (§13 Q6) |

Each fixture also runs twice in one test to assert byte-identical output, and once with shuffled
input order to assert the order of the inputs makes no difference.

---

## 13. Open questions (must be answered before Accepted)

1. **Self-rating vs minimum evidence.** §2.2 uses the self-rating "until a valid test result
   exists"; §2.4 says fewer than 2 answered questions means no numerical score. When a sub-skill
   has a self-rating and 0–1 answered questions:
   (a) the self-rating gives it a numerical score, shown as self-rated, until 2 questions are
   answered; or
   (b) it is "Not enough evidence", and the self-rating is displayed but never scored.
2. **Self-rating scale and level.** Is it out of 10, and does 7/10 mean 70.0%? Is it recorded per
   sub-skill, per skill or per domain? If per skill or domain, does it apply to every sub-skill
   underneath?
3. **Skipped questions and the minimum.** A skipped question scores 0. Does it count as one of
   the 2 answered questions? (a) yes, every question presented counts; (b) no, only questions the
   student actually answered.
4. **Scope of "latest attempt".** (a) Per sub-skill: the latest attempt that contains questions
   on that sub-skill. A topic test on Graphs then updates Graphs and leaves everything else as it
   was. (b) The single latest attempt overall. Every sub-skill that attempt did not cover then
   loses its test result.
5. **Questions tagged to several sub-skills.** The question format allows several sub-skill tags
   with weights. (a) Allow one tag per question only; (b) the question counts in full (100 or 0)
   for each tagged sub-skill; (c) the tag weights split it.
6. **Roll-up with missing evidence.** When some sub-skills in a skill or domain are "Not enough
   evidence": (a) leave them out and average the rest by effort hours; (b) the skill or domain is
   also "Not enough evidence" until every sub-skill has a score; (c) a coverage threshold you
   specify. Also say what overall readiness does when a weighted domain has no score.
7. **Requirement level.** Are required % and criticality set per (a) sub-skill only, or (b) at
   any level (sub-skill, skill or domain), with the gap computed at that level? The brief's
   examples use both "DSA: High" and "Graphs: 75%".
8. **Target match with missing evidence.** A requirement whose score is "Not enough evidence"
   (a) counts as ratio 0; (b) is left out, and the screen says how many requirements are
   covered; or (c) target match is not shown until every requirement has a score.
9. **Band boundaries and criticality.** Is a gap of exactly 10 MEDIUM, and exactly 25 MEDIUM? Is
   criticality used only in the §8.3 tie-break, or also somewhere else? If elsewhere, give the
   rule.
10. **Rounding.** (a) Round half up (62.45 → 62.5). (b) Round half to even. Also: are gaps and
    ratios calculated from the rounded, stored scores (so the screen always adds up), or from
    unrounded values?
11. **Prerequisites.** Confirm they do **not** affect scores, only roadmap order.
12. **Readiness bands.** The brief shows 80 and above Strong, 60–79.9 Moderate, below 60 Needs
    improvement. Should 1.0 use those? Are they global, or set per requirement version like the
    domain weights?
