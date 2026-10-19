CREATE TABLE rooms (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Public identifier, used in the share link; the internal id is never exposed.
    code           VARCHAR(8)   NOT NULL,
    story_id       UUID         NOT NULL REFERENCES stories (id),
    -- A room does not outlive its creator's account (GDPR).
    creator_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    password_hash  VARCHAR(255) NOT NULL,
    -- Capacity chosen by the creator, between 2 and the story maximum.
    player_count   INTEGER      NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'WAITING',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT rooms_code_uk         UNIQUE (code),
    CONSTRAINT rooms_code_format     CHECK (code ~ '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$'),
    CONSTRAINT rooms_player_count_ck CHECK (player_count BETWEEN 2 AND 4),
    CONSTRAINT rooms_status_ck       CHECK (status IN ('WAITING', 'PLAYING', 'FINISHED'))
);

CREATE INDEX rooms_story_idx ON rooms (story_id);
CREATE INDEX rooms_creator_idx ON rooms (creator_id);

CREATE TABLE room_participants (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID        NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    user_id     UUID        REFERENCES users (id) ON DELETE CASCADE,
    -- Guests have no row in users: their id only lives in their JWT, hence no foreign key.
    guest_id    UUID,
    pseudo      VARCHAR(32) NOT NULL,
    color       VARCHAR(7)  NOT NULL,
    is_creator  BOOLEAN     NOT NULL DEFAULT false,
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT room_participants_identity_ck CHECK ((user_id IS NULL) <> (guest_id IS NULL)),
    CONSTRAINT room_participants_color_uk    UNIQUE (room_id, color)
);

CREATE UNIQUE INDEX room_participants_user_uk ON room_participants (room_id, user_id)
    WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX room_participants_guest_uk ON room_participants (room_id, guest_id)
    WHERE guest_id IS NOT NULL;
CREATE UNIQUE INDEX room_participants_creator_uk ON room_participants (room_id) WHERE is_creator;
