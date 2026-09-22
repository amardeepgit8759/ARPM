# ADR-003: Single-tenant, user-owned data — no organization concept

- **Status:** Accepted
- **Date:** 2026-09-15
- **Supersedes:** the multi-tenancy requirement in the original Phase 0 brief

## Context

The founding brief for this project required multi-tenancy from day one: an `organization_id`
on every user-owned table, every query scoped by tenant, and an explicit cross-tenant access
test in every slice.

Placefy's actual product is centred on the individual student. A student sets a target company
and role, gets assessed, and receives a readiness score, a ranked gap list and a dated plan.
They own their assessments, coding data, skill progress, roadmap and history. Nothing in the
product's scope puts a customer organization above the user — no college administrator with a
cohort view, no placement cell, no seat-based billing, no invitations.

Adding a tenant abstraction anyway would mean a column on every table that always holds the
same value as the user's own tenant, a scoping predicate on every query that can never exclude
anything, and a cross-tenant access test that passes because there is only ever one tenant.
That last point is the decisive one: **a defence that cannot fail is a defence nobody is
testing.** It provides the appearance of isolation while the real isolation boundary — one user
from another — goes unexamined.

## Decision

Placefy is a single-application, individual-user product. There is no organization or tenant
concept.

The `users` table carries no `organization_id`, `tenant_id`, `org_owner` or `invite_token`.
`POST /api/v1/auth/register` creates a student account directly and returns a session. No
organization is provisioned, and no invite token is required.

**The enforcement discipline from the original rule is kept, retargeted from tenant to user:**

- Every query for user-owned data is scoped by the subject of the verified JWT.
- That scoping lives in the use-case layer, not in a controller and not in a repository.
- Every slice carries an explicit test proving that user A cannot read user B's data.
- No endpoint accepts a user identifier from a path, query or body as the subject of a read.
  `GET /api/v1/me` resolves the user from the token and there is deliberately no
  `GET /api/v1/users/{id}`, so there is no identifier to substitute.

## Consequences

**Good.** The schema says what is true. The isolation test exercises the boundary that actually
exists and would fail if it broke. No dead column, no predicate that is always satisfied, no
ceremony that new code has to imitate without understanding why.

**Costs.** If Placefy ever sells to colleges, introducing organizations is a real migration:
a new table, a backfill deciding which organization each existing user belongs to, a nullable
`organization_id` column on every user-owned table that then has to be made non-null, and a
revisit of every query written under this ADR. That is a deliberate, bounded, one-time cost
taken at the point where the requirement becomes real, rather than an unbounded ongoing cost
paid on every table and every query from now until then.

**This does not mean isolation is optional.** "No tenancy" is not "no scoping". A query for
user-owned data that is not scoped by the token subject is a bug of the same severity a
cross-tenant leak would have been.

## Alternatives considered

**Multi-tenancy from day one, as originally specified.** The standard advice, and correct for a
B2B product. Rejected because Placefy is not one, and because the cross-tenant test it mandates
would have been vacuous — passing for the whole life of the product without ever exercising the
condition it names.

**A single seeded default organization, with real tenancy deferred.** Keeps the column so a
future migration is cheaper. Rejected for the same reason: every user shares one tenant, so the
scoping predicate is inert and the test is vacuous, but now the code also carries an abstraction
that misrepresents the domain to everyone who reads it.

**Row-level security in PostgreSQL, scoped by user.** Genuinely strong — the database refuses
cross-user reads even if application code forgets. Worth revisiting. Not adopted now because it
puts an authorization rule somewhere the use-case tests cannot see, and at this size the
use-case layer plus an explicit cross-user test is the boundary that is easiest to keep honest.
