CREATE TABLE users (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    pseudo         VARCHAR(32)  NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    role           VARCHAR(16)  NOT NULL DEFAULT 'USER',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ,
    CONSTRAINT users_pseudo_uk     UNIQUE (pseudo),
    CONSTRAINT users_pseudo_format CHECK (pseudo ~ '^[a-z]+-[0-9]{6}$'),
    CONSTRAINT users_role_ck       CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE refresh_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64) NOT NULL UNIQUE,
    family_id    UUID        NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at);
