package com.example.back.room;

import com.example.back.auth.AuthExceptionHandler.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RoomController.class)
public class RoomExceptionHandler {

    @ExceptionHandler(RoomException.class)
    ResponseEntity<ApiError> refused(RoomException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage()));
    }

    /** Malformed JSON, or a value of the wrong type (e.g. a text as player count). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable() {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", "Requête invalide."));
    }
}
