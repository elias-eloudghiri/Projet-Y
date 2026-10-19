CREATE TABLE stories (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(120)  NOT NULL,
    description   VARCHAR(2000) NOT NULL,
    player_count  INTEGER       NOT NULL,
    -- Object key of the PDF in the S3 bucket; images are embedded in the PDF.
    pdf_key       VARCHAR(255)  NOT NULL,
    pdf_size      BIGINT        NOT NULL,
    -- Kept when the admin account is deleted (GDPR).
    created_by    UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT stories_pdf_key_uk      UNIQUE (pdf_key),
    CONSTRAINT stories_player_count_ck CHECK (player_count BETWEEN 2 AND 4)
);

CREATE UNIQUE INDEX stories_title_uk ON stories (lower(title));

CREATE TABLE story_characters (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    story_id     UUID        NOT NULL REFERENCES stories (id) ON DELETE CASCADE,
    name         VARCHAR(60) NOT NULL,
    -- Display order; the narrator comes first (0).
    position     INTEGER     NOT NULL,
    is_narrator  BOOLEAN     NOT NULL DEFAULT false,
    CONSTRAINT story_characters_position_uk UNIQUE (story_id, position)
);

CREATE UNIQUE INDEX story_characters_name_uk ON story_characters (story_id, lower(name));
CREATE UNIQUE INDEX story_characters_narrator_uk ON story_characters (story_id) WHERE is_narrator;
