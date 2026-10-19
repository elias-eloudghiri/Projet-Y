package com.example.back.story;

import org.springframework.http.HttpStatus;

/** A story import refused, with a message meant for the administrator. */
public class StoryValidationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public StoryValidationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static StoryValidationException invalid(String message) {
        return new StoryValidationException(HttpStatus.BAD_REQUEST, "INVALID_STORY", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
