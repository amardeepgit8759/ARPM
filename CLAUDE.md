# Placefy — working rules

Placement-readiness platform. A pre-final-year engineering student sets a target company and
role, gets assessed, and receives a deterministic readiness score per sub-skill, a ranked list
of gaps against that company's hiring bar, and a dated preparation plan that recalculates after
every assessment.

Read this before changing anything. It is the standing contract for the codebase, not a summary
of it.

---

## 1. The constraint that defines this product

**The Decision Layer computes. The Narrative Layer phrases.**

The Decision Layer — everything in `domain` and `application` — produces every score, gap and
roadmap. Rule-based, deterministic, pure functions of stored inputs. No ML, no LLM, no
randomness, and no clock reads inside the logic: time and identifiers arrive through the
`TimeProvider` and `IdGenerator` ports. Same input, same output, forever.

The Narrative Layer phrases results that are already computed. It cannot produce a number,
change a gap, reorder a ranking, or influence any input to a computation. This is enforced by
**validating its output in code**, never by instructing the model in a prompt.

**If a change would let a generated model influence a number, the change is wrong.** See
`docs/adr/002-decision-narrative-separation.md`.

---

## 2. Architecture rules

1. `domain` has zero framework imports — no Spring, no JPA, no Jackson, no HTTP types.
2. `application` is **also** framework-free. Use cases are plain objects wired by hand in
   `infrastructure/config/UseCaseConfiguration`. Transaction boundaries are decorators in that
   same package, because `@Transactional` is a Spring annotation.
3. JPA entities live in `infrastructure/persistence` and are **not** the domain models. Mappers
   between them are hand-written, both directions.
4. Dependency direction: `web → application → domain`. `infrastructure` implements ports in
   `application/port/out`. Nothing points outward. (`web` may use domain value objects such as
   `UserId`; that is still inward.)
5. Controllers never touch repositories.
6. **Placefy has no tenant or organization concept.** It is a single-user product. No
   `organization_id`, no tenant provisioning, no invite-token registration, no default
   organization. Isolation is per user: every query for user-owned data is scoped by the subject
   of the verified JWT, scoping lives in the use-case layer, and every slice carries an explicit
   test that user A cannot read user B's data. See `docs/adr/003-single-user-data-ownership.md`.
7. Scores are computed on write and persisted as immutable runs with `engine_version`,
   `config_version`, `taxonomy_version` and `inputs_hash`. Read endpoints are projections.
   **Nothing recomputes a score on read.**
8. Thresholds, weights, band boundaries, pacing rules and taxonomy live in the database as
   versioned configuration. No magic numbers in Java.
9. Every external system sits behind a port. The product must pass its full test suite with
   every external provider returning empty.

`backend/src/test/java/com/placefy/architecture/ArchitectureRulesTest.java` enforces 1, 2, 4, 5
and the no-clock/no-randomness rule. When a model client is added, add it to that test's
prohibited list **in the same commit**.

---

## 3. Stack — locked

| Area     | Choice |
|----------|--------|
| Backend  | Java 17, Spring Boot 3.5, Gradle 8.14 (Kotlin DSL), Clean Architecture |
| Data     | PostgreSQL 16, Spring Data JPA + Hibernate, Flyway |
| Auth     | Spring Security, stateless JWT (HS256), BCrypt cost 12 |
| Frontend | React 18 + TypeScript strict, Vite, Tailwind, TanStack Query, React Hook Form + Zod, Recharts |
| Testing  | JUnit 5, Mockito, jqwik, ArchUnit, Testcontainers, Vitest, Playwright |
| Ops      | Docker Compose, GitHub Actions |

Do not add a dependency without saying what it replaces and why the standard library or the
existing stack cannot do it.

Deliberately **not** yet installed, because nothing uses them: Mockito is unused (hand-written
fakes are clearer for these use cases and are what the tests use), Recharts and Playwright
arrive with the first chart and the first end-to-end flow.

---

## 4. How to work

- Small, reviewable increments. One coherent slice per turn.
- State the plan in 5–10 lines before writing code for anything non-trivial, and wait for
  approval. After approval, build the whole slice without stopping for details you can
  reasonably decide.
- Do not scaffold future phases. No placeholder files, no TODO stubs. Build what the current
  step needs, complete and tested.
- Write the test with the code, never after.
- Record long-term decisions as an ADR in `docs/adr/NNN-title.md`: Context, Decision,
  Consequences, Alternatives considered.
- If an instruction conflicts with these rules, say so and propose the alternative rather than
  quietly complying.
- If a product rule is unclear, ask one specific question. Do not invent business logic and do
  not invent data.
- **Never invent company hiring thresholds, question content, or statistics.** If data is needed
  and has not been supplied, ask for it or leave a clearly marked seed file in `content/`.
- Keep responses terse. Show the diff and the reasoning, not a description of what code
  normally does.

---

## 5. Definition of done — every slice

1. Domain logic is pure and unit-tested.
2. Use case is tested with fake ports.
3. Repository is tested against Testcontainers PostgreSQL.
4. Endpoint has a contract test and is in `docs/api/openapi.yaml`, updated in the same commit.
5. Frontend has loading, empty, error and success states. All four.
6. Cross-user access is tested and denied.
7. Any number rendered on screen traces to a persisted scoring run.
8. CI is green. No skipped or disabled tests.

---

## 6. Never acceptable

- A number on screen with no persisted run behind it
- Framework annotations in `domain`
- A repository called from a controller
- Business logic inside a React component
- An LLM call anywhere in the path of computing a score
- A secret committed to the repository
- A skipped test merged to main

---

## 7. Commands

```bash
# Backend — run from backend/
./gradlew test               # domain, use cases, ArchUnit           (fast, no Docker)
./gradlew integrationTest    # Testcontainers PostgreSQL + contract  (needs Docker)
./gradlew check              # both

# Frontend — run from frontend/
npm run typecheck
npm run test
npm run build

# Whole stack — run from the repository root
docker compose -f ops/docker-compose.yml up --build
```

`PLACEFY_JWT_SECRET` has no committed default and the application refuses to start without at
least 32 bytes. That is intentional. For local work, put one in `ops/.env` — see
`ops/.env.example`.

---

## 8. Where things are

```
backend/src/main/java/com/placefy/
  domain/{user,session,taxonomy}/ aggregates and value objects — framework-free
  application/port/in/        what the web layer calls
  application/port/out/       what infrastructure implements
  application/service/        use cases — framework-free
  infrastructure/persistence/ JPA entities, Spring Data repos, adapters, mappers
  infrastructure/security/    BCrypt, JWT, Spring Security config
  infrastructure/system/      the only clock read and the only UUID source
  infrastructure/config/      hand-wiring and transaction decorators
  infrastructure/content/     YAML content loading, validation and startup seeding
  web/                        controllers, HTTP representations, RFC 7807 advice

backend/src/main/resources/db/migration/   Flyway owns the schema; Hibernate is `validate`
docs/adr/                                  decisions with consequences
docs/api/openapi.yaml                      the committed API contract
docs/scoring-spec.md                       READ FIRST for anything touching scoring, gaps or
                                           roadmaps. Currently a SKELETON: until every
                                           REPLACE_ME is filled in and its status is Accepted,
                                           the scoring engine is not implementable and must
                                           not be started.
content/                                   reviewed YAML content — see its README
content/taxonomy/SCHEMA.md                 the taxonomy content format and its rules
ops/                                       compose, Dockerfiles, env template
.github/workflows/ci.yml                   CI (GitHub only reads workflows from here)
```
