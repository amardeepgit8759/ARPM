# ADR-004: Refresh token rotation with family revocation, in an httpOnly cookie

- **Status:** Accepted
- **Date:** 2026-09-15

## Context

Placefy issues a 15-minute access token. Something has to let a student stay signed in across
that boundary and across page reloads without retyping a password, and that something is a
long-lived credential worth stealing.

Two questions follow: where the client keeps it, and what happens when a copy is stolen.

## Decision

**Storage.** The access token lives in JavaScript memory only — not `localStorage`, not
`sessionStorage`. Anything script can read, injected script can read and exfiltrate. The refresh
token lives in a cookie the page cannot read at all: `HttpOnly`, `Secure`, `SameSite=Strict`,
`Path=/api/v1/auth/refresh`. Only a SHA-256 digest of it is stored server-side, so a database
dump yields no usable sessions.

**Rotation.** Every call to `/refresh` retires the presented token and issues a new one. A token
is therefore valid for exactly one use.

**Family revocation on replay.** Tokens issued in one unbroken chain share a `family_id`. If a
token that has already been rotated is presented again, the server cannot distinguish a
legitimate client retrying from a thief using a stolen copy — so it assumes theft and revokes
the entire family. The honest user is signed out and signs in again; the thief gets nothing, and
the theft leaves a trace. Other sessions belonging to the same user, from other logins, are
untouched.

**CSRF.** `SameSite=Strict` on a cookie scoped to the single endpoint that reads it is the
control. A cross-site request cannot carry the cookie, so there is nothing for a forged request
to authenticate with. Spring Security's CSRF token machinery is therefore disabled; a session
cookie it would otherwise protect does not exist.

**Every rejection is identical.** Missing, unknown, expired, replayed and orphaned tokens all
produce `urn:placefy:problem:invalid-refresh-token` with the same message, and the response
expires the cookie so the client stops retrying.

**Lifetimes.** Access token 15 minutes, as specified. Refresh token 30 days — an engineering
default, not a product decision, and the one number here that should be revisited by someone who
knows how often students actually return.

## Consequences

**Good.** A stolen refresh token is usable at most once before the theft is detected and the
chain is killed. An XSS that gets a foothold gets a token with a 15-minute ceiling, not a
durable session. Page reload costs one silent `/refresh`.

**Costs.** A refresh-token table with a row per rotation, which needs periodic pruning of
expired rows — not yet implemented, and required before this runs anywhere real. The "restoring"
state on app startup is a real UI state that every protected route has to handle rather than a
detail. And a legitimate client that double-submits a refresh — two tabs waking together, a
retried request after a flaky response — gets signed out. That is the price of not being able to
tell that case from theft, and it is the right side to err on.

**One subtle requirement, learned the hard way.** The transaction around `/refresh` is annotated
`noRollbackFor = InvalidRefreshTokenException`. Replay detection revokes the family and *then*
rejects the request; under a plain `@Transactional` the rejection rolls the revocation back, so
the attacker is told "no" while every token in the family stays alive. The defence would appear
to work and do nothing. This was caught by the contract test, not by review — any change to the
transaction boundary here needs that test to stay.

## Alternatives considered

**Refresh token in `localStorage`.** Simpler: no cookie, no CORS credentials, no `SameSite`
reasoning. Rejected — it puts the durable credential exactly where injected script can read it,
which defeats the point of having a short-lived access token at all.

**Long-lived access token, no refresh.** No second credential and no rotation machinery.
Rejected because it removes any bound on the damage from a stolen token; revocation would then
require server-side session state, which is the stateless-JWT decision unwound.

**Rotation without family revocation.** Retire the presented token, ignore replays. Simpler and
avoids signing out the double-submitting client. Rejected because it makes theft undetectable:
a thief who refreshes before the honest client simply becomes the session, and nobody learns
anything.

**A CSRF token alongside the cookie.** Defence in depth and standard practice. Not adopted for
Phase 0 because `SameSite=Strict` on a path-scoped cookie already denies the attack, and an
unnecessary mechanism is one more thing to keep correct. Worth revisiting if a future endpoint
ever authenticates by cookie.
