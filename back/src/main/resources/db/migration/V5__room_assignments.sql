-- Attribution board (US-06). Increased on every change of the shared room state (joins, moves,
-- start), so that clients can drop a broadcast older than the state they display.
ALTER TABLE rooms ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0;

-- Target of the composite foreign key below.
ALTER TABLE room_participants ADD CONSTRAINT room_participants_room_id_uk UNIQUE (room_id, id);

-- One row per character of the room's story, created with the room and only ever updated: a
-- character always has exactly one player.
CREATE TABLE room_assignments (
    room_id         UUID NOT NULL REFERENCES rooms (id) ON DELETE CASCADE,
    character_id    UUID NOT NULL REFERENCES story_characters (id),
    participant_id  UUID NOT NULL,
    CONSTRAINT room_assignments_pk PRIMARY KEY (room_id, character_id),
    -- The player belongs to the same room. No cascade: a player leaving with characters must
    -- hand them over first (departure handling comes with a later story).
    CONSTRAINT room_assignments_participant_fk FOREIGN KEY (room_id, participant_id)
        REFERENCES room_participants (room_id, id)
);

CREATE INDEX room_assignments_participant_idx ON room_assignments (participant_id);

-- Rooms created before this migration: every character goes to the creator.
INSERT INTO room_assignments (room_id, character_id, participant_id)
SELECT r.id, c.id, p.id
FROM rooms r
JOIN story_characters c ON c.story_id = r.story_id
JOIN room_participants p ON p.room_id = r.id AND p.is_creator;
