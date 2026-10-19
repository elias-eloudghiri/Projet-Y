-- The story player count becomes a maximum: each room picks its own count between 2 and it (US-04).
ALTER TABLE stories RENAME COLUMN player_count TO max_players;
ALTER TABLE stories RENAME CONSTRAINT stories_player_count_ck TO stories_max_players_ck;
