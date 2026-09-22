-- Placefy V1: the two tables authentication needs, and nothing else.
--
-- Placefy is a single-user product: a student owns their own assessments, roadmap and
-- progress. There is deliberately no organization_id or tenant_id here. Isolation is
-- per user, enforced in the use-case layer against the subject of the verified JWT.
-- See docs/adr/003-single-user-data-ownership.md.
--
-- All timestamps are timestamptz. PostgreSQL stores them as UTC instants; the application
-- sets hibernate.jdbc.time_zone=UTC so nothing depends on the server's local zone.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT users_updated_at_not_before_created_at CHECK (updated_at >= created_at)
);

-- The application normalises addresses to lower case before they reach this table, so a
-- plain unique index is a correct uniqueness rule and there is no functional index to keep
-- in step with the Java. This is also the authority under concurrency: the register use
-- case checks first for a clean error message, but only this index can actually prevent a
-- duplicate when two requests race.
CREATE UNIQUE INDEX users_email_key ON users (email);

CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  UUID        NOT NULL,
    -- VARCHAR, not CHAR: bpchar pads on read, which would silently turn a 64-character digest
    -- into a padded string that no longer equals the value that was written.
    token_hash VARCHAR(64) NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT refresh_tokens_expire_after_issue CHECK (expires_at > issued_at),
    CONSTRAINT refresh_tokens_revoked_after_issue CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);

-- Only the SHA-256 digest of a token is ever stored, so a database dump yields no usable
-- sessions. Lookup on refresh is by this digest, hence the unique index.
CREATE UNIQUE INDEX refresh_tokens_token_hash_key ON refresh_tokens (token_hash);

-- Replay detection revokes an entire rotation chain at once.
CREATE INDEX refresh_tokens_family_id_idx ON refresh_tokens (family_id);

CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);
