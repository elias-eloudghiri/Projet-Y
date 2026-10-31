package com.example.back.room;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** A room request refused, with a message meant for the player. */
public class RoomException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Duration retryAfter;

    public RoomException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    private RoomException(HttpStatus status, String code, String message, Duration retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    static RoomException invalid(String message) {
        return new RoomException(HttpStatus.BAD_REQUEST, "INVALID_ROOM", message);
    }

    /** Also used when the room exists but the caller is not in it, so as not to reveal it. */
    static RoomException notFound() {
        return new RoomException(
                HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Cette room n'existe pas.");
    }

    /** 403, not 401: 401 is kept for a missing or invalid JWT. */
    static RoomException wrongPassword() {
        return new RoomException(
                HttpStatus.FORBIDDEN, "ROOM_WRONG_PASSWORD", "Mot de passe incorrect.");
    }

    static RoomException notParticipant() {
        return new RoomException(
                HttpStatus.FORBIDDEN, "ROOM_FORBIDDEN", "Tu ne fais pas partie de cette room.");
    }

    static RoomException full() {
        return new RoomException(HttpStatus.CONFLICT, "ROOM_FULL", "Cette room est complète.");
    }

    static RoomException alreadyStarted() {
        return new RoomException(
                HttpStatus.CONFLICT,
                "ROOM_ALREADY_STARTED",
                "La lecture a déjà commencé dans cette room.");
    }

    static RoomException notCreator() {
        return new RoomException(
                HttpStatus.FORBIDDEN,
                "ROOM_NOT_CREATOR",
                "Seul le créateur de la room peut faire ça.");
    }

    static RoomException characterNotFound() {
        return new RoomException(
                HttpStatus.NOT_FOUND,
                "CHARACTER_NOT_FOUND",
                "Ce personnage ne fait pas partie de cette histoire.");
    }

    static RoomException playerNotFound() {
        return new RoomException(
                HttpStatus.BAD_REQUEST, "PLAYER_NOT_FOUND", "Ce joueur n'est pas dans la room.");
    }

    static RoomException notReady(String message) {
        return new RoomException(HttpStatus.CONFLICT, "ROOM_NOT_READY", message);
    }

    static RoomException tooManyAttempts(Duration retryAfter) {
        // Rounded up: retrying after the announced delay must succeed.
        long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        return new RoomException(
                HttpStatus.TOO_MANY_REQUESTS,
                "ROOM_TOO_MANY_ATTEMPTS",
                "Trop de mots de passe erronés. Réessaie dans " + seconds + " s.",
                Duration.ofSeconds(seconds));
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Set on 429 answers only. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
