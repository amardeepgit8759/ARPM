-- Placefy V2: the skill taxonomy.
--
-- A taxonomy version is the vocabulary every later phase attaches to: assessment items are
-- tagged with sub-skill codes, company requirements set thresholds per sub-skill, and a
-- scoring run records which taxonomy_version produced it. That last point drives the shape
-- here: once a version is published it is frozen, because changing its content afterwards
-- would silently rewrite the meaning of results already stored against it.
--
-- Content rows (domains, skills, sub-skills, prerequisites) are insert-only. Nothing updates
-- them. The only column that ever changes after insert is taxonomy_versions.status, and only
-- once, on the DRAFT -> PUBLISHED transition.
--
-- "ordinal" rather than "position": POSITION is a SQL standard function name, and letting
-- Hibernate emit it unquoted is a portability problem waiting to happen.

CREATE TABLE taxonomy_versions (
    id              UUID        PRIMARY KEY,
    -- Author-chosen in the content manifest, not auto-incremented. This is the value a scoring
    -- run stores as taxonomy_version, so it must be traceable to reviewed content.
    label           VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    -- SHA-256 of the content files this version was built from. Re-importing the same label
    -- with different content is rejected; this is what makes that check possible.
    source_checksum VARCHAR(64) NOT NULL,
    imported_at     TIMESTAMPTZ NOT NULL,
    published_at    TIMESTAMPTZ,

    CONSTRAINT taxonomy_versions_status_known CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT taxonomy_versions_published_consistent CHECK (
        (status = 'DRAFT' AND published_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
    ),
    CONSTRAINT taxonomy_versions_published_after_import CHECK (
        published_at IS NULL OR published_at >= imported_at
    ),
    CONSTRAINT taxonomy_versions_checksum_is_sha256 CHECK (source_checksum ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX taxonomy_versions_label_key ON taxonomy_versions (label);

CREATE INDEX taxonomy_versions_published_idx
    ON taxonomy_versions (published_at DESC)
    WHERE status = 'PUBLISHED';


CREATE TABLE taxonomy_domains (
    id                  UUID         PRIMARY KEY,
    taxonomy_version_id UUID         NOT NULL REFERENCES taxonomy_versions (id) ON DELETE CASCADE,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(160) NOT NULL,
    ordinal             INTEGER      NOT NULL,

    CONSTRAINT taxonomy_domains_ordinal_non_negative CHECK (ordinal >= 0)
);

CREATE UNIQUE INDEX taxonomy_domains_version_code_key ON taxonomy_domains (taxonomy_version_id, code);
CREATE UNIQUE INDEX taxonomy_domains_version_ordinal_key ON taxonomy_domains (taxonomy_version_id, ordinal);


CREATE TABLE taxonomy_skills (
    id                  UUID         PRIMARY KEY,
    -- Denormalised from the parent domain so the database can enforce the same global
    -- uniqueness rule the domain model enforces: a skill code is unique across the whole
    -- version, not merely within its domain.
    taxonomy_version_id UUID         NOT NULL REFERENCES taxonomy_versions (id) ON DELETE CASCADE,
    taxonomy_domain_id  UUID         NOT NULL REFERENCES taxonomy_domains (id) ON DELETE CASCADE,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(160) NOT NULL,
    ordinal             INTEGER      NOT NULL,

    CONSTRAINT taxonomy_skills_ordinal_non_negative CHECK (ordinal >= 0)
);

CREATE UNIQUE INDEX taxonomy_skills_version_code_key ON taxonomy_skills (taxonomy_version_id, code);
CREATE UNIQUE INDEX taxonomy_skills_domain_ordinal_key ON taxonomy_skills (taxonomy_domain_id, ordinal);


CREATE TABLE taxonomy_sub_skills (
    id                   UUID         PRIMARY KEY,
    taxonomy_version_id  UUID         NOT NULL REFERENCES taxonomy_versions (id) ON DELETE CASCADE,
    taxonomy_skill_id    UUID         NOT NULL REFERENCES taxonomy_skills (id) ON DELETE CASCADE,
    code                 VARCHAR(64)  NOT NULL,
    name                 VARCHAR(160) NOT NULL,
    -- Vocabulary only in this slice. No scoring semantics are attached to it anywhere; what it
    -- means for gap cost is a decision for docs/scoring-spec.md, which is not yet written.
    default_effort_hours INTEGER      NOT NULL,
    ordinal              INTEGER      NOT NULL,

    CONSTRAINT taxonomy_sub_skills_ordinal_non_negative CHECK (ordinal >= 0),
    CONSTRAINT taxonomy_sub_skills_effort_in_range CHECK (default_effort_hours BETWEEN 1 AND 10000)
);

CREATE UNIQUE INDEX taxonomy_sub_skills_version_code_key ON taxonomy_sub_skills (taxonomy_version_id, code);
CREATE UNIQUE INDEX taxonomy_sub_skills_skill_ordinal_key ON taxonomy_sub_skills (taxonomy_skill_id, ordinal);


CREATE TABLE taxonomy_sub_skill_prerequisites (
    id                        UUID    PRIMARY KEY,
    sub_skill_id              UUID    NOT NULL REFERENCES taxonomy_sub_skills (id) ON DELETE CASCADE,
    prerequisite_sub_skill_id UUID    NOT NULL REFERENCES taxonomy_sub_skills (id) ON DELETE CASCADE,
    ordinal                   INTEGER NOT NULL,

    -- The database can rule out the trivial cycle and the duplicate edge. It cannot rule out a
    -- longer cycle without a recursive trigger, so acyclicity is enforced in the domain model
    -- (TaxonomyVersion) and asserted by the content validator before anything is ever inserted.
    CONSTRAINT taxonomy_prerequisites_not_self CHECK (sub_skill_id <> prerequisite_sub_skill_id),
    CONSTRAINT taxonomy_prerequisites_ordinal_non_negative CHECK (ordinal >= 0)
);

CREATE UNIQUE INDEX taxonomy_prerequisites_edge_key
    ON taxonomy_sub_skill_prerequisites (sub_skill_id, prerequisite_sub_skill_id);

CREATE INDEX taxonomy_prerequisites_prerequisite_idx
    ON taxonomy_sub_skill_prerequisites (prerequisite_sub_skill_id);
