# ADR-007: Student and administrator roles

- **Status:** Accepted
- **Date:** 2026-09-23
- **Amends:** ADR-003 (single-user data ownership), which did not yet cover administrators

## Context

The product brief adds an Admin module. Administrators will manage companies, job roles,
requirements, questions, the taxonomy and roadmap rules, and see student analytics. Until now
`Role.ADMIN` existed in code but no endpoint used it.

Two questions had to be settled before the first admin endpoint:

1. **How does someone become an administrator?** A registration flag, a config property that
   names an email, or an API that grants the role are all privilege-escalation surfaces.
2. **What student data can an administrator see?** ADR-003 makes every user's data readable by
   that user alone. "Student analytics" could be read as permission to browse individual
   students.

This is not multi-tenancy. There is still no organization and no `organization_id` (ADR-003
stands). An administrator manages platform content and reads platform-wide aggregates. They do
not own a group of students.

## Decision

**Granting ADMIN is a database action only.** Registration always creates a STUDENT. No
endpoint, request field or configuration property grants ADMIN. An operator promotes an existing
account with one SQL statement, which leaves a trace outside the application:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'someone@example.com';
```

The promoted user signs in again to get a token carrying the new role.

**Role checks happen twice.**

1. The security filter chain requires `ROLE_ADMIN` on `/api/v1/admin/**`, mapped from the
   access token's `role` claim.
2. Every admin use case reads the role from **storage** and refuses a non-admin with
   `AdminAccessRequiredException` (403, `urn:placefy:problem:forbidden`).

The second check is the real gate. It means a demotion takes effect on the next request rather
than when the 15-minute token expires, and a mistake in the URL rules cannot expose an admin
operation on its own.

**Administrators see aggregates, never individuals.** Admin analytics endpoints return counts,
distributions and averages across students. No admin endpoint returns a student's identity,
answers, scores, gaps or roadmap. ADR-003's rule holds unchanged: a student's data is readable
by that student alone. Each admin endpoint carries a cross-user test proving its response names
no student.

**The caller's own profile (`/api/v1/students/me`) serves administrators too.** The path names
the product's subject, not a role gate; an administrator still needs to read and edit their own
name and password.

## Consequences

**Good.** No code path can escalate privilege, because none can write the role. Demotion is
immediate. Student privacy does not depend on how an administrator uses the dashboard: the API
has no way to show them an individual.

**Costs.** Creating the first administrator needs database access, which is one more deployment
step (documented in the README). Every admin use case loads the requesting user once to check
the role, a primary-key read per call.

**If per-student support tooling is ever needed** (an administrator helping one named student),
that is a new decision with its own consent and audit design. It is not an extension of the
analytics endpoints.

## Alternatives considered

**Bootstrap the first admin from environment variables.** Convenient, but it is a
configuration property that grants a role, which `Role.java` already ruled out. A leaked or
mistaken environment file would silently create an administrator.

**Trust the token's role claim alone.** Simpler, but a demoted administrator would keep access
until the token expired, and the URL rule would be the only barrier.

**Let administrators view individual students.** Useful for support, but it contradicts ADR-003
and the product's per-user ownership model. Rejected until a real support need justifies a
separately designed, audited feature.
