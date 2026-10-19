package com.example.back.room;

import org.springframework.http.HttpStatus;

/** A room request refused, with a message meant for the player. */
public class RoomException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public RoomException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static RoomException invalid(String message) {
        return new RoomException(HttpStatus.BAD_REQUEST, "INVALID_ROOM", message);
    }

    /** Also used when the room exists but the caller is not in it, so as not to reveal it. */
    static RoomException notFound() {
        return new RoomException(
                HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Cette room n'existe pas.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
