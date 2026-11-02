package com.example.back.room;

public enum RoomStatus {
    /** Created, players are joining. */
    WAITING,
    /** Reading started: the creator may still change the attribution (US-07). */
    PLAYING,
    /** Reading over (not used yet). */
    FINISHED
}
