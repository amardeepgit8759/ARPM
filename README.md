# Placefy

A placement-readiness platform. A pre-final-year engineering student sets a target company and
role, gets assessed, and receives a deterministic readiness score per sub-skill, a ranked list
of the gaps between them and that company's hiring bar, and a dated preparation plan that
recalculates itself after every assessment.

**Status: Phase 0 complete, plus slice 2A (taxonomy vocabulary).** Foundation, the
authentication vertical slice, and the skill taxonomy that everything downstream attaches to.
No assessment, scoring or roadmap yet — and the scoring engine is deliberately blocked, see
[`docs/scoring-spec.md`](docs/scoring-spec.md).

---

## The one thing to understand first

Placefy has two layers, and the boundary between them is absolute.

**The Decision Layer** computes every score, every gap and every roadmap. It is rule-based and
deterministic — pure functions of stored inputs, with no ML, no LLM, no randomness and no clock
reads inside the logic. The same assessment produces the same readiness score today and in three
years.

**The Narrative Layer** only phrases results the Decision Layer has already computed. It cannot
produce a number, change a gap or make a decision. That is enforced by validating its output in
code, not by asking a model to behave.

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

As of slice 2A: **222 backend unit tests, 51 backend integration tests, 27 frontend tests. 0
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
content/     reviewed YAML content — taxonomy lives here; see its README
.github/     CI
```

CI lives in `.github/workflows/` rather than `ops/` because GitHub reads workflows from nowhere
else. Flyway migrations live in `backend/src/main/resources/db/migration` so the application and
the tests replay identical SQL from one source.

### Backend packages

```text
com.placefy
  domain/{user,session,taxonomy}  aggregates and value objects — zero framework imports
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

**Auth** — register, login, refresh with rotation, and `GET /api/v1/me`. BCrypt cost 12,
15-minute HS256 access token, refresh token in an `HttpOnly; Secure; SameSite=Strict` cookie
scoped to `/api/v1/auth/refresh`. RFC 7807 problem responses throughout, with stable `urn:`
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

- **No logout endpoint.** Signing out clears the tab; the refresh cookie stays valid until it
  expires or rotates. Server-side revocation needs an endpoint Phase 0 does not have.
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
- **`ADMIN` exists but nothing uses it yet.** The role was added with no API that grants it;
  promotion is a database action. The JWT-claim-to-authority mapping arrives with 2C's first
  admin endpoint rather than sitting unused.
- **The OpenAPI spec is not machine-verified against the running API.** The contract tests and
  the spec are kept in step by hand; enforcing it needs a dependency that has not been justified
  yet.
