# APRM — Automated Placement Readiness Mentor

A placement-readiness platform. A pre-final-year engineering student sets a target company and
role, gets assessed, and receives a deterministic readiness score per sub-skill, a ranked list
of the gaps between them and that company's hiring bar, and a dated preparation plan that
recalculates itself after every assessment.

**Status: authentication and accounts are complete, with Student and Admin roles.** Also built:
the skill taxonomy, the narrative safety kernel, the roadmap planning engine and the question
authoring pipeline. There is no assessment, scoring or roadmap yet. The scoring engine is
deliberately blocked until [`docs/scoring-spec.md`](docs/scoring-spec.md) is accepted.

The codebase predates the APRM name and still calls itself Placefy internally: the
`com.placefy` package, `PLACEFY_*` environment variables and `urn:placefy:problem:*` error types.
Those identifiers are stable and stay as they are.

**No AI, ML or LLM is used anywhere in the current version.** Scores, gaps, priorities, roadmaps
and explanations all come from deterministic rules, and a build rule rejects AI/ML libraries.
See [ADR-006](docs/adr/006-no-ai-or-ml-in-the-current-version.md).

---

## The one thing to understand first

APRM has two layers, and the boundary between them is absolute.

**The Decision Layer** computes every score, every gap and every roadmap. It is rule-based and
deterministic — pure functions of stored inputs, with no ML, no LLM, no randomness and no clock
reads inside the logic. The same assessment produces the same readiness score today and in three
years.

**The Narrative Layer** only phrases results the Decision Layer has already computed. It cannot
produce a number, change a gap or make a decision. In this version it is a rule-based
explanation engine that fills templates from a decision trace, and its output is still checked
in code by the narrative validator.

Students make real decisions on these numbers — what to study, where to apply, whether they are
ready this cycle. A score that drifts between identical inputs, or that can't be explained six
months later, isn't a measurement. Everything in the architecture follows from refusing that.

The rule is enforced today, before there is any scoring code to protect: an ArchUnit test fails
the build if anything in `domain` or `application` reads a clock or a random source.

See [ADR-002](docs/adr/002-decision-narrative-separation.md).

---

## Running it

You need Docker, and that is all — the toolchain lives in the images.

```bash
# 1. Generate a signing secret. There is no committed default, on purpose:
#    a fallback key in the repository is the same as no signature at all.
printf 'PLACEFY_JWT_SECRET=%s\n' "$(openssl rand -base64 48)" > ops/.env

# 2. Bring up PostgreSQL, the backend and the frontend.
docker compose -f ops/docker-compose.yml up --build
```

On Windows PowerShell, step 1 is:

```powershell
"PLACEFY_JWT_SECRET=$([Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 })))" |
  Set-Content ops/.env
```

Then open <http://localhost:5173>, register, and you are signed in. The API is on
<http://localhost:8080>.

If 5432 or 8080 is already taken on your machine — both are popular — override the published
ports in `ops/.env` rather than editing the compose file. The frontend's API base URL and the
backend's allowed CORS origin follow them automatically:

```properties
PLACEFY_DB_PORT=55432
PLACEFY_API_PORT=18080
PLACEFY_WEB_PORT=15173
```

To stop, and to discard the database volume:

```bash
docker compose -f ops/docker-compose.yml down -v
```

### Working on it directly

```bash
# PostgreSQL only, for running the backend from an IDE
docker compose -f ops/docker-compose.yml up postgres

cd backend  && ./gradlew bootRun --args='--spring.profiles.active=local'
cd frontend && npm install && npm run dev
```

`bootRun` needs `PLACEFY_JWT_SECRET` in the environment. The `local` profile turns off the
`Secure` flag on the refresh cookie, because a Secure cookie is never sent over plain-HTTP
localhost and sign-in would silently never persist.

### Creating an administrator

Registration always creates a student, and no endpoint or setting can grant the admin role
([ADR-007](docs/adr/007-student-and-admin-roles.md)). To make an administrator, register the
account normally, then promote it in the database:

```bash
docker compose -f ops/docker-compose.yml exec postgres \
  psql -U placefy -d placefy -c "UPDATE users SET role = 'ADMIN' WHERE email = 'you@example.com';"
```

Sign out and back in to get a token carrying the new role. Demoting works the same way with
`'STUDENT'`, and takes effect on the next request.

---

## Tests

```bash
cd backend
./gradlew test             # domain, use cases, ArchUnit — fast, no Docker needed
./gradlew integrationTest  # Testcontainers PostgreSQL + endpoint contract — needs Docker
./gradlew check            # both

cd frontend
npm run typecheck
npm run test
```

Currently: **396 backend unit tests, 60 backend integration tests, 27 frontend tests. 0
skipped.** A skipped test does not get merged.

`./gradlew test` also validates the real YAML in `content/taxonomy` — see below.

The two backend suites are split by JUnit tag rather than by source set, so CI can report
"architecture rules" and "integration" as separate, legible steps.

### What the tests are actually for

- **Domain tests** pin behaviour that must never drift: an expiry boundary is inclusive,
  registration is deterministic given the same inputs, a password is measured in UTF-8 bytes
  rather than characters. jqwik properties cover email normalisation across generated input.
- **Use-case tests** run against hand-written fakes with a fixed clock and a counting id
  generator, so they assert exact ids and exact instants rather than "some UUID".
- **Repository tests** run against a real PostgreSQL through Testcontainers, with Flyway
  applying the same migrations the application applies and Hibernate set to `validate`. A
  mapping that drifts from the schema fails here. It already caught one: `CHAR(64)` silently
  space-pads on read, so the token column is `VARCHAR(64)`.
- **Contract tests** exercise the HTTP surface end to end. One of them caught a real defect —
  see "A bug worth knowing about" below.
- **ArchUnit rules** were verified by deliberately violating three of them and confirming each
  failed, then reverting.
- **Roadmap property tests** (jqwik) assert the invariants across generated plans: never over
  the daily cap, never a dependent before its prerequisite, never past the horizon, identical
  output for identical input — plus a coverage property that fails if the generator stops
  producing drops, split topics or prerequisite chains, since every invariant is trivially true
  of an empty plan.
- **The narrative safety harness** runs 100 generated traces through ten hostile generators and
  asserts that not one corrupted output is accepted, and that the deterministic fallback is
  accepted for every trace. 1,100 checks per build.
- **Question content validation** does the same for `content/questions`, with twelve broken
  fixtures asserting each authoring rule fires — plus assertions that each one fails for the
  reason its name claims, since `MALFORMED_CONTENT` covers enough ground that a fixture could
  otherwise pass for an unrelated failure.
- **Content validation** parses the real files in `content/taxonomy` and feeds them to the
  domain, so a malformed content file fails the ordinary build. Twelve deliberately-broken
  fixtures under `src/test/resources/taxonomy-invalid/` assert that each rule actually fires.
- **Frontend tests** stub `fetch`, not the API module, so the real client runs: headers,
  credentials, RFC 7807 parsing and Zod response validation are all under test.

---

## Layout

```text
backend/     Java 17, Spring Boot 3.5, Gradle Kotlin DSL, Clean Architecture
frontend/    Vite, React 18, TypeScript strict, Tailwind
ops/         docker-compose.yml, Dockerfiles, nginx config, .env.example
docs/adr/    architectural decisions, with their consequences
docs/api/    openapi.yaml — the committed API contract
content/     reviewed YAML content — taxonomy and questions; see its README
.github/     CI
```

CI lives in `.github/workflows/` rather than `ops/` because GitHub reads workflows from nowhere
else. Flyway migrations live in `backend/src/main/resources/db/migration` so the application and
the tests replay identical SQL from one source.

### Backend packages

```text
com.placefy
  domain/{user,session,taxonomy,question,narrative,roadmap}  aggregates, kernels, engines
  application/port/in         what the web layer calls
  application/port/out        what infrastructure implements
  application/service         use cases — also framework-free
  infrastructure/persistence  JPA entities, Spring Data repos, adapters, explicit mappers
  infrastructure/security     BCrypt, JWT, Spring Security configuration
  infrastructure/system       the only clock read and the only UUID source in the application
  infrastructure/config       hand-wiring of use cases, transaction boundaries
  infrastructure/content      YAML loading, validation and startup seeding
  web                         controllers, HTTP representations, RFC 7807 advice
```

Dependencies point strictly inward. `application` is framework-free too, which is stricter than
the usual Spring arrangement: use cases are plain objects constructed in
`UseCaseConfiguration`, so a use-case test needs no application context. [ADR-001](docs/adr/001-clean-architecture.md)
explains the cost and the alternatives.

---

## What Phase 0 built

**Schema** (`V1`) — `users` and `refresh_tokens`. UUID primary keys, `timestamptz` throughout,
CHECK constraints on timestamp ordering. No `organization_id`: Placefy is a single-user product
and isolation is per user, scoped by the JWT subject in the use-case layer.
[ADR-003](docs/adr/003-single-user-data-ownership.md) records that decision and what adding
organizations later would cost.

**Auth** — register, login, refresh with rotation, logout, and `GET /api/v1/students/me`.
BCrypt cost 12, 15-minute HS256 access token, refresh token in an
`HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`. RFC 7807 problem responses throughout, with stable `urn:`
types a client can branch on.

Three details that are load-bearing rather than incidental:

- **Refresh tokens rotate, and replay revokes the whole chain.** A token is valid for exactly
  one use. Presenting a rotated token is treated as theft — the server cannot tell a replaying
  client from a thief — so every token in that rotation family dies.
  [ADR-004](docs/adr/004-refresh-token-rotation.md).
- **Login spends the same work on an unknown address as on a known one**, verifying against a
  decoy hash. Without that, response time is an account-enumeration oracle. A wrong password, an
  unknown address and a malformed address all return the identical response.
- **The access token never touches `localStorage`.** It lives in memory; the refresh cookie is
  unreadable by script. A page reload therefore has a genuine "restoring session" state, which
  the router waits for rather than flashing the login screen at signed-in users.

**Frontend** — routing shell, auth context, login and register screens, and a protected
dashboard. Every screen has all four states: loading, empty, error, success.

## What slice 2A built

The **taxonomy**: domains, skills and sub-skills, with a prerequisite graph over sub-skills.
This is Placefy's vocabulary — assessment items will be tagged with sub-skill codes, company
requirements will set thresholds per sub-skill, and every scoring run records which taxonomy
version produced it.

Content lives as reviewed YAML in [`content/taxonomy/`](content/taxonomy/) and is loaded into
the database as an immutable, versioned row set. The format is documented in
[`content/taxonomy/SCHEMA.md`](content/taxonomy/SCHEMA.md).

Four decisions worth knowing:

- **Sub-skill codes are globally unique, not unique within their skill.** Prerequisites cross
  skill and domain boundaries — database indexing genuinely depends on balanced trees — so a
  flat namespace is what makes a reference unambiguous. It also means a sub-skill can be moved
  between skills later without changing its code, and therefore without orphaning the scores
  recorded against it.
- **The prerequisite graph must be acyclic**, checked in the domain model. If `a` requires `b`
  and `b` requires `a`, no study order satisfies either; the validator prints the cycle path.
- **A version label means one exact set of content, forever.** The importer stores a SHA-256 of
  the files. Re-importing a label with different content is refused rather than merged, because
  two scoring runs both citing "2026.1" while having used different vocabularies would make
  neither reproducible. Line endings are normalised first, so the checksum does not depend on
  whether git checked the files out on Windows or Linux.
- **The validator reports every problem at once, not the first.** Content authoring is an
  edit-run-edit loop; a file with six mistakes should cost one run to diagnose, not six.

## Accounts, sessions and roles

- `POST /api/v1/auth/logout` revokes the cookie's session (its whole rotation family) and clears
  the cookie. It needs no access token, so an expired one cannot trap anyone signed in, and it
  always answers 204.
- `PUT /api/v1/students/me` changes the display name. The email is the sign-in identifier and is
  not editable.
- `POST /api/v1/students/me/password` needs the current password. It revokes **every** session
  of the user on every device and returns a fresh one to the caller, so the device that made the
  change stays signed in.
- `GET /api/v1/admin/overview` is the first admin endpoint. It returns counts only; no admin
  endpoint exposes an individual student. The role is checked from the token and again from
  the database, so demotion is immediate.

The frontend renews an expired access token by itself: on an `unauthenticated` 401 it refreshes
once, however many requests are waiting, and retries. A 401 for a wrong password is never
retried. Students and administrators get separate menus. Entries for modules that are not built
yet open a page that says so, with no sample figures.

## Account data: export and deletion

`GET /api/v1/students/me/export` returns everything APRM holds about the caller, as a JSON
attachment. `DELETE /api/v1/students/me` erases it. Both are on the Profile page.

Both resolve the user from the verified token's subject and accept no identifier, so neither can
be pointed at somebody else.

- **Deletion requires the current password**, on top of a valid access token. A token can be
  stolen; re-entering a password is the one check a hijacked session cannot pass, and this is the
  only irreversible operation in the product.
- **It is a hard delete.** A user who asks to be deleted and is instead marked `deleted = true`
  has not been deleted. Sessions cascade; any future user-owned table must cascade too, or
  deletion will start failing on a foreign key.
- **The export is assembled field by field**, never serialised from whatever objects are in
  scope. That is the one place "include everything" and "include no secrets" pull in opposite
  directions, and a reflective dump resolves it the wrong way — a new column would silently join
  the export. Session entries carry metadata only; token hashes are absent by design, and a test
  asserts the stored hashes appear nowhere in the output.

## Security

- **Cross-user isolation is tested on every endpoint.** Placefy has no tenants, so user-to-user
  is the real boundary ([ADR-003](docs/adr/003-single-user-data-ownership.md)) — which makes this
  the suite privacy actually rests on, rather than a formality that passes because there is only
  one tenant. One test enumerates every mapped endpoint from Spring's own handler mapping and
  fails if one appears that is neither declared public nor covered, so the suite cannot be
  forgotten as the API grows.
- **Secret scanning** runs in CI (gitleaks, full history) and in an optional pre-commit hook.
  Install it with `git config core.hooksPath ops/hooks`. The hook uses gitleaks when present and
  otherwise falls back to a narrow check for what this repository can actually leak; CI runs the
  full scan regardless, so a machine without gitleaks cannot push a secret past the gate.
- **OWASP dependency check** runs as its own CI job, failing on CVSS ≥ 7.0 against runtime
  dependencies only. **It requires an `NVD_API_KEY` repository secret** — the job fails loudly
  without one rather than scanning an empty database and reporting a clean result, which would
  look like a pass.

## The question authoring pipeline

Questions are the measuring instrument. A wrong answer key does not merely annoy a student — it
moves the sub-skill score their whole preparation plan is built from, and they cannot tell. So
the authoring rules are stricter than the taxonomy's, and `./gradlew test` fails the build on any
question that breaks one.

Format and reasoning: [`content/questions/SCHEMA.md`](content/questions/SCHEMA.md).

**`content/questions/` ships empty, deliberately.** The pipeline is here; the questions are not.
Placefy's rules forbid inventing question content, and a bank seeded with plausible-looking
placeholders is worse than an empty one because nobody can tell which entries were reviewed.

Two required blocks make this more than a schema check:

- **Originality attestation.** A question bank for interview preparation is the most tempting
  place in this product to paste in somebody's real assessment — a copyright problem and a
  fairness problem, invisible once it is in the bank. So `original_work: true` is structural:
  `false` is a rejection, not a note, and a source note is required either way.
- **Reviewer sign-off**, where **the reviewer may not be the author**. Self-review is not review,
  and it is enforced rather than encouraged.

**Duplicate detection** runs across the whole bank: identical stems after normalising case,
punctuation and spacing; and near-identical stems by overlap of three-word runs. Trigrams rather
than single words, because word overlap alone flags any two questions on the same topic — they
share the subject's vocabulary — while shared three-word runs indicate shared phrasing.

**The coverage report** lists every sub-skill in the taxonomy against its question count by
difficulty, driven by the taxonomy rather than the bank, so a sub-skill with no questions is a
row of zeros instead of an absent row. It applies no threshold for what "enough" means — that is
a scoring decision, not a counting one.

**Tag weights are carried, not normalised.** Whether they must sum to one is open in
`docs/scoring-spec.md` §5.2, and deciding it here would answer it silently for every score
computed afterwards.

## The roadmap planning engine

Takes a prioritised set of sub-skills and produces a dated plan. Pure `domain` — no clock, no
randomness — so replanning the same request tomorrow gives the identical plan, and a "your plan
changed because…" diff is attributable to changed evidence rather than to when the button was
pressed.

It orders by prerequisites (Kahn's algorithm, ties broken by the caller's priority), checks
capacity against weekly hours and the remaining horizon, packs days under a minute cap with at
most N new sub-skills per day, schedules spaced revision at the configured offsets, places
assessment checkpoints on cadence, and keeps a reserved final window free of new material.

**What does not fit is dropped whole and named.** Effort is never shrunk to make a topic fit, and
a topic that cannot be scheduled leaves no half-session behind. A dropped prerequisite drops its
dependents, with that given as the reason. Three separate lists say what could not be done:
`droppedSubSkills`, `unscheduledRevisions`, and any empty day. A plan that showed only what was
scheduled would let a student believe it covers their gaps when a third were left out.

Every pacing value — daily cap, sub-skills per day, revision offsets, checkpoint cadence,
reserved window — is supplied by the caller. The planner has no defaults, because those are
product decisions and CLAUDE.md rule 8 keeps them out of Java.

**What the planner does not decide:** which sub-skills belong in a plan, their priority order, or
how taxonomy hours become planned minutes. All three arrive as inputs from gap analysis, which is
why the planner is testable before a scoring engine exists.

## The narrative safety kernel

Built before any model integration, deliberately. It is the code that stands between a generated
sentence and a student, and it is pure `domain` — no framework, no network, no clock.

A `DecisionTrace` is everything a narrative is allowed to know about one scoring run: the overall
score, target match, ranked gaps with their score, threshold and criticality, strengths,
consistency, and an `allowed_numbers` set. A generator receives that and nothing else; whatever
it writes is checked back against that and nothing else.

`NarrativeValidator` refuses text that states a number the run did not produce, names a sub-skill
the run did not measure, omits a required section, invents one, or breaks the length bounds.
Refusal falls back to `DecisionTraceRenderer`, which turns the same trace into prose while
performing no arithmetic at all — a test asserts its own output passes the validator, for all 100
generated traces.

Three details that took the most care:

- **Numbers are matched by value, with no tolerance.** 62 and 62.0 are the same claim; "about 60"
  against a computed 62 is not, and is refused however gently it reads.
- **Sub-skill names are masked before extraction.** The shipped taxonomy contains "Two-Pointer
  Technique" and codes like `dp-on-grids`; extracting digits first would make every correct
  mention of them assert a number.
- **Spelled-out numerals count.** "Your readiness is sixty-two" walks straight past a digits-only
  check, and writing numbers as words is exactly what a fluent model does.

**A known limit, pinned as a test:** the validator checks *which* numbers may appear, not which
fact each belongs to. A narrative that states a real threshold as though it were the overall score
passes. Closing that needs the trace to say where each figure may be used, not merely that it
exists; today the mitigation is that the deterministic renderer, not the model, labels each figure.

`default_effort_hours` and the sub-skill structure are **vocabulary only**. Nothing reads them
to compute anything. What effort means for gap cost is a decision for `docs/scoring-spec.md`.

**The example content is an example.** The three domains shipped under `content/taxonomy/domains/`
are a worked demonstration of the format, not a researched curriculum, and every file says so in
its header. Replace them with the real taxonomy and bump the version.

**The dashboard shows no readiness score**, and that is the point. It shows an explicit "No
assessment yet" panel instead. A number on a Placefy screen is a claim that a scoring run
measured something; a placeholder zero is a different and false claim, and a test asserts no
zero is rendered.

---

## A bug worth knowing about

The contract test for refresh-token replay failed on first run, and it was not the test's fault.

Replay detection revokes the compromised token family and then rejects the request. Under a
plain `@Transactional`, throwing rolls that revocation straight back — so an attacker replaying
a stolen token would be told "no" while every token in the family stayed alive. The defence
would have looked like it worked and done nothing, and no unit test would have noticed, because
with fake ports there is no transaction to roll back.

The fix is `noRollbackFor = InvalidRefreshTokenException` on
`TransactionalRefreshSession`. If you touch the transaction boundary around `/refresh`, that
contract test is what stands between you and reintroducing this.

---

## Design

One neutral palette, one functional accent, 1px borders, no gradients and no shadow-heavy cards.
Tabular figures everywhere — scores and dates sit in vertical lists and change between renders,
and proportional digits make them jitter, which reads as instability in numbers whose whole
promise is that they are stable.

Colour carries meaning only: the accent marks the single action on a screen, danger marks a
failure. Nothing is coloured for decoration.

---

## Conventions

- **The API contract is `docs/api/openapi.yaml`**, and it is updated in the same commit as the
  endpoint it describes.
- **Flyway owns the schema.** Hibernate is `ddl-auto: validate` and never alters anything.
- **No secret is committed.** `PLACEFY_JWT_SECRET` has no default and the application refuses to
  start without at least 32 bytes. `ops/.env` is gitignored; `ops/.env.example` documents it.
- **No dependency is added without saying what it replaces.** Mockito, Recharts and Playwright
  are in the locked stack but not installed, because nothing uses them yet.

[CLAUDE.md](CLAUDE.md) holds the full working rules, including the definition of done applied to
every slice.

---

## Not done yet

Honest list, so nobody is surprised:

- **No pruning of expired refresh tokens.** The table grows a row per rotation. Required before
  this runs anywhere real.
- **The 30-day refresh lifetime is an engineering default**, not a product decision. Someone who
  knows how often students return should set it.
- **The 12-character password minimum is likewise a placeholder** with no product decision behind
  it.
- **No rate limiting** on login or register.
- **No catalog API yet.** Slice 2A stops at domain, schema, content and seeding. Companies,
  roles and requirement sets are 2B; the admin publish endpoint and the `/api/v1/catalog/*`
  read endpoints are 2C, which is why `docs/api/openapi.yaml` gained nothing this slice.
- **`docs/scoring-spec.md` is a skeleton.** Until it is filled in and marked Accepted, the
  readiness engine, Preview Mode and gap analysis cannot be built without inventing the very
  numbers the architecture exists to keep honest.
- **Access tokens outlive logout by up to 15 minutes.** They are stateless; logout revokes the
  refresh side and the client discards its copy. A denylist would need server-side state.
- **The OpenAPI spec is not machine-verified against the running API.** The contract tests and
  the spec are kept in step by hand; enforcing it needs a dependency that has not been justified
  yet.
