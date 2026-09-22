# Taxonomy content format

The taxonomy is Placefy's vocabulary. Assessment items are tagged with sub-skill codes, company
requirements set thresholds per sub-skill, and every scoring run records which taxonomy version
produced it. Everything downstream attaches here, so this format is deliberately strict:
`./gradlew test` fails the build if any file in this directory breaks a rule below.

## Layout

```text
content/taxonomy/
  SCHEMA.md               this file
  taxonomy.yaml           the manifest: version label + the list of domains
  domains/
    _TEMPLATE.yaml        starting point for a new domain; ignored by the loader
    <domain-code>.yaml    one file per domain, named for its code
```

The file name and the `code` inside it must match. A file present in `domains/` but not listed
in the manifest is an error rather than an omission — otherwise a finished domain could sit on
disk, never be published, and nobody would notice.

Files beginning with `_` are templates and are skipped.

## The manifest — `taxonomy.yaml`

```yaml
taxonomy_version: "2026.1"
description: Optional free text.
domains:
  - data-structures-algorithms
  - databases
  - operating-systems
```

| Field | Required | Rule |
|---|---|---|
| `taxonomy_version` | yes | 1–64 chars, letters/digits/`.`/`_`/`-`, starting alphanumeric |
| `description` | no | free text |
| `domains` | yes | at least one domain code, each with a matching file |

### Bump the version when you change content

A version label must mean one exact set of content forever, because scoring runs cite it.
Importing a label that already exists with different content is **rejected**, not merged — the
importer compares a SHA-256 of the files. If you edited a domain, bump `taxonomy_version`.

## A domain file — `domains/<code>.yaml`

```yaml
code: data-structures-algorithms
name: Data Structures and Algorithms
description: Optional free text.

skills:
  - code: arrays-and-strings
    name: Arrays and Strings
    sub_skills:
      - code: array-traversal
        name: Array Traversal
        default_effort_hours: 4
        prerequisites: []

      - code: two-pointer-technique
        name: Two-Pointer Technique
        default_effort_hours: 6
        prerequisites:
          - array-traversal
```

| Field | Required | Rule |
|---|---|---|
| `code` | yes | lower-case kebab-case, ≤ 64 chars; must equal the file name |
| `name` | yes | 2–160 chars |
| `description` | no | free text |
| `skills` | yes | at least one |
| `skills[].code` | yes | lower-case kebab-case, **unique across the whole taxonomy** |
| `skills[].name` | yes | 2–160 chars |
| `skills[].sub_skills` | yes | at least one |
| `sub_skills[].code` | yes | lower-case kebab-case, **unique across the whole taxonomy** |
| `sub_skills[].name` | yes | 2–160 chars |
| `sub_skills[].default_effort_hours` | yes | integer, 1–10000 |
| `sub_skills[].prerequisites` | no | sub-skill codes, may cross domains; omit or `[]` for none |

Unknown keys are rejected. `prerequisits: [x]` is an error, not a silently ignored line.

## Codes

Codes are lower-case kebab-case: `graph-traversal`, `sql-joins`, `os2`. No underscores, no
dots, no capitals, no leading/trailing or doubled hyphens.

**Sub-skill and skill codes are globally unique, not unique within their parent.** Prerequisites
reference sub-skills across skill and domain boundaries, so a flat namespace is what makes a
reference unambiguous. It also means a sub-skill can be moved to a different skill later without
changing its code — and therefore without orphaning the scores recorded against it. Pick a code
that reads correctly on its own: `sql-joins`, not `joins`.

Renaming a code is a breaking change. Names can be reworded freely; codes cannot.

## Prerequisites

Prerequisites form a directed acyclic graph over sub-skills.

- A prerequisite must name a sub-skill declared somewhere in this taxonomy.
- A sub-skill may not require itself.
- The same prerequisite may not be listed twice.
- **The graph must have no cycles.** If `a` requires `b` and `b` requires `a`, no study order
  can satisfy either, and the validator reports the full cycle path.
- Cross-domain prerequisites are expected and correct. Database indexing genuinely depends on
  understanding balanced trees, and saying so here is the point of a single flat namespace.

## `default_effort_hours`

An estimate of the study time a sub-skill needs, in whole hours.

**It currently carries no scoring meaning.** Nothing reads it to compute a score, a cost or a
gap priority. What it means for gap cost is a decision for `docs/scoring-spec.md`, which is not
yet written. Treat the numbers in the example files as placeholders to review, not as research.

## Validation

Every rule above is checked by `TaxonomyContentValidationTest`, which parses the real files in
this directory on every `./gradlew test` run. There is no separate lint step to remember and no
extra CI job — a malformed content file fails the ordinary build.

The validator reports **every** problem it finds at once, not the first, so a file with six
mistakes takes one run to diagnose rather than six.

## Adding a domain

1. Copy `domains/_TEMPLATE.yaml` to `domains/<your-code>.yaml`.
2. Fill it in. Replace every `REPLACE_ME`.
3. Add the code to the `domains` list in `taxonomy.yaml`.
4. Bump `taxonomy_version`.
5. Run `./gradlew test` from `backend/`.
