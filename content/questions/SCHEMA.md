# Question bank content format

Questions are the measuring instrument. A wrong answer key does not merely annoy a student — it
moves the sub-skill score their whole preparation plan is built from, and they have no way to
tell. So this format is stricter than the taxonomy's, and `./gradlew test` fails the build if any
question in this directory breaks a rule below.

**This directory ships empty.** The pipeline is here; the questions are not. Placefy's working
rules forbid inventing question content, and a bank seeded with plausible-looking placeholders is
worse than an empty one because nobody can tell which entries were actually reviewed.

## Layout

```text
content/questions/
  SCHEMA.md              this file
  questions.yaml         the manifest: bank version, taxonomy version, list of bank files
  banks/
    _TEMPLATE.yaml       starting point; ignored by the loader
    <name>.yaml          a set of questions
```

A file in `banks/` that the manifest does not list is an error, not an omission — otherwise a
finished set of questions could sit on disk and never be asked.

Files beginning with `_` are templates and are skipped.

## The manifest — `questions.yaml`

```yaml
bank_version: "2026.1"
taxonomy_version: "2026.1-example"
description: Optional free text.
banks:
  - dsa-arrays
  - dbms-sql
```

`taxonomy_version` is what tags are checked against. It must match the taxonomy the bank is
validated with — a tag that resolved last month may name a sub-skill a newer taxonomy renamed or
removed, and silently accepting that would attach responses to a sub-skill nobody is measuring.

## A question

Every field below is required.

| Field | Rule |
|---|---|
| `code` | lower-case kebab-case, ≤ 80 chars, **unique across the whole bank** |
| `type` | `SINGLE_CHOICE` or `MULTIPLE_CHOICE` |
| `difficulty` | integer 1–5 |
| `stem` | 15–4000 chars |
| `options` | ≥ 2, unique ids, unique text |
| `correct_options` | must name declared ids; exactly 1 for single, ≥ 2 for multiple; never all of them |
| `explanation` | 20–4000 chars |
| `tags` | ≥ 1, each a sub-skill code in the declared taxonomy version, positive weight |
| `originality` | `attested_by`, `attested_on`, `original_work: true`, `source_note` (≥ 10 chars) |
| `review` | `reviewed_by`, `reviewed_on`, `outcome: APPROVED`, `comment` |

Unknown keys are rejected. `explanaton:` is an error, not a question that quietly ships without
an explanation.

## Codes are permanent

A response records the code it answered. Renaming or reusing a code severs a stored response from
the question it was given, which corrupts every calibration statistic computed afterwards. Stems
may be reworded and options reordered freely; codes may not change.

## Why originality is a required block

A question bank built for interview preparation is the most tempting place in this product to
paste in somebody's real assessment. That is a copyright problem and a fairness problem, and it is
invisible once the question is in the bank — a reproduced item looks exactly like an original one.

So the attestation is structural rather than advisory:

- `original_work` must be `true`. There is no approved route into the bank for a reproduced
  question; `false` is a rejection, not a note.
- `source_note` is required even so, so there is always a sentence on the record saying where the
  question came from.
- `attested_by` is a named person, not a team.

## Why review is a required block

- `outcome` must be `APPROVED`. `CHANGES_REQUESTED` and `REJECTED` keep the question out.
- **The reviewer must not be the author.** Self-review is not review, and this is enforced.
- `reviewed_on` must not precede `attested_on` — a question cannot be reviewed before it was
  written.

## Duplicate detection

Two checks, both across the entire bank:

1. **Identical stems.** Stems are compared after lower-casing and stripping punctuation and
   repeated whitespace, so two copies of the same question are caught however differently they
   were typed.
2. **Near-identical stems.** Overlap of three-word runs, above a configured threshold. Trigrams
   rather than single words because word overlap alone flags any two questions on the same
   topic — they share the subject's vocabulary — while shared three-word runs indicate shared
   phrasing.

A student who meets the same question in a Quick Check and again in a mock has been measured once
and told they were measured twice.

## Tag weights are not normalised

Weights must be positive. They are **not** required to sum to 1, and nothing rescales them.

Whether they should is an open question in `docs/scoring-spec.md` §5.2. Deciding it here would
answer it silently for every score computed afterwards, so the bank carries what the author wrote
and leaves the decision where it belongs.

## Coverage

`BankCoverageReport` lists every sub-skill in the taxonomy against its question count by
difficulty — driven by the taxonomy, not the bank, so a sub-skill with no questions appears as a
row of zeros rather than being absent. An assessment cannot measure a sub-skill it has no
questions for, and without the report the only symptom is a readiness score quietly built on a
narrower base than it appears to cover.

The report applies no threshold for what "enough" means. How many questions a sub-skill needs
before it can be scored is a scoring decision, not a counting one.

## Adding questions

1. `cp banks/_TEMPLATE.yaml banks/<your-bank-name>.yaml`
2. Fill it in. Replace every `REPLACE_ME`.
3. Add `<your-bank-name>` to `banks` in `questions.yaml`.
4. Bump `bank_version`.
5. From `backend/`: `./gradlew test`
