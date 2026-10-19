package com.example.back.room;

import java.util.Arrays;
import java.util.List;

/**
 * Player colours of a room, one per player (rooms hold at most 4). Source of truth for the front;
 * all are readable as text colour on the dark background.
 */
public enum RoomColor {
    BLUE("#60A5FA"),
    PINK("#F472B6"),
    AMBER("#FBBF24"),
    GREEN("#34D399");

    /** In allocation order: the creator gets the first one. */
    public static final List<String> PALETTE = Arrays.stream(values()).map(RoomColor::hex).toList();

    private final String hex;

    RoomColor(String hex) {
        this.hex = hex;
    }

    public String hex() {
        return hex;
    }
}
