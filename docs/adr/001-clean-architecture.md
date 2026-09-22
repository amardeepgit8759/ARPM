# ADR-001: Clean Architecture in a single Gradle module, enforced by ArchUnit

- **Status:** Accepted
- **Date:** 2026-09-15

## Context

Placefy's value is a deterministic scoring engine. Its scores, gap rankings and roadmaps must
be pure functions of stored inputs, reproducible years later from a persisted run. That
property survives only if the code computing it is isolated from anything that can change
underneath it: a framework upgrade, an ORM's lazy-loading behaviour, an HTTP request's
lifecycle, a clock.

A generative Narrative Layer will eventually sit alongside this engine, phrasing results the
engine has already computed. The boundary between "decides" and "describes" has to be a
structural fact in the codebase, not a convention people remember.

We therefore need layers with an enforced dependency direction, and we need the innermost layer
to have no framework on its classpath at all.

## Decision

Four layers under `com.placefy`, dependencies pointing strictly inward:

```
web  ──▶  application  ──▶  domain
              ▲
       infrastructure
```

- **`domain`** — aggregates and value objects. Zero framework imports: no Spring, no JPA, no
  Jackson, no servlet types. Immutable. Never reads a clock.
- **`application`** — use cases and the ports they speak through. `port/in` is what the web
  layer calls; `port/out` is what infrastructure implements. **Also framework-free**, which
  goes beyond the usual Spring Clean Architecture setup.
- **`infrastructure`** — adapters: JPA persistence, BCrypt, JWT, the system clock. Implements
  `port/out`. Depends inward and is depended on by nothing.
- **`web`** — controllers and HTTP representations. Calls `port/in`, maps to and from domain
  value objects, and never touches a repository.

A **single Gradle module**, not four. The module graph would enforce direction at compile time,
but ArchUnit enforces the same rules on every build, covers rules a module graph cannot express
(no clock reads, no field injection, controllers not reaching repositories), and keeps the build
file readable by anyone joining.

Because the application layer carries no `@Service`, use cases are constructed by hand in
`infrastructure/config/UseCaseConfiguration`, and transaction boundaries are supplied by thin
decorators in the same package.

`ArchitectureRulesTest` is the enforcement. Its rules were verified by deliberately violating
three of them and confirming each failed, before reverting.

## Consequences

**Good.** A use case is testable with three lines and no application context; the full
application suite runs in about a second. A reader can determine what a class may depend on
from its package alone. When the scoring engine arrives, its purity is a build failure away
from being lost rather than a code-review habit.

**Costs.** Explicit mappers between domain models and JPA entities, written by hand both ways.
One `@Bean` method per use case instead of an annotation. One decorator class per transactional
use case. Roughly 40% more files than an anaemic-service layout for the same behaviour.

**Accepted friction.** The web layer is permitted to reference domain value objects such as
`UserId`. That is still an inward dependency; forbidding it would push parsing into the
application layer for no gain.

## Alternatives considered

**Multi-module Gradle build (`domain`, `application`, `infrastructure`, `web`).** Enforces
direction at compile time, which is stronger than a test. Rejected for now because ArchUnit
covers the same ground plus rules the module graph cannot express, and a four-module build
slows every compile and complicates the Docker build for a team of two. If the module count
grows or the rules start being argued with, this is the upgrade path and it is mechanical.

**Spring annotations in the application layer (`@Service`, `@Transactional` on use cases).**
The common arrangement and materially less code. Rejected because it makes "the application
layer is framework-free" a matter of degree, and a rule with exceptions is a rule that erodes.
The cost is bounded — one configuration class and three decorators.

**Package-private visibility alone, no ArchUnit.** Free, but cannot express the rules that
matter most here: it says nothing about clock reads, and nothing about direction across
packages that must be public.
