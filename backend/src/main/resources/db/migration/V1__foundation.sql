-- Turn 1: users, businesses, membership, refresh tokens.
-- Every timestamp is timestamptz (criterion 1.15). Constraints live here rather
-- than only in Java, so a path that bypasses the service layer still cannot
-- write inconsistent data.

CREATE TABLE users (
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    full_name     TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_email_not_blank CHECK (length(btrim(email)) > 0)
);

-- Case-insensitive uniqueness: Bookly@x.com and bookly@x.com are one account,
-- otherwise registration silently creates a second account the user cannot find.
CREATE UNIQUE INDEX users_email_lower_key ON users (lower(email));

CREATE TABLE businesses (
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL,
    slug       TEXT        NOT NULL UNIQUE,
    timezone   TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT businesses_slug_shape CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE business_members (
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT business_members_unique UNIQUE (business_id, user_id),
    -- Only the roles turn 1 actually enforces. MANAGER and SUPER_ADMIN are on the
    -- out-of-scope list: five declared roles would not be verifiable, two are.
    CONSTRAINT business_members_role_known CHECK (role IN ('BUSINESS_OWNER', 'EMPLOYEE'))
);

-- The membership lookup is on the hot path of every tenant-scoped request.
CREATE INDEX business_members_user_idx ON business_members (user_id);
CREATE INDEX business_members_business_idx ON business_members (business_id);

CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- All tokens descended from one login share a family. Presenting a token that
    -- was already rotated means it was captured, so the family is revoked whole.
    family_id   UUID        NOT NULL,
    token_hash  TEXT        NOT NULL UNIQUE,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
