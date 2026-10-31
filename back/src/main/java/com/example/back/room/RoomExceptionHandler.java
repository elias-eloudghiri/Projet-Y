package com.example.back.room;

import com.example.back.auth.AuthExceptionHandler.ApiError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = RoomController.class)
public class RoomExceptionHandler {

    @ExceptionHandler(RoomException.class)
    ResponseEntity<ApiError> refused(RoomException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(e.status());
        if (e.retryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.retryAfter().toSeconds()));
        }
        return response.body(new ApiError(e.code(), e.getMessage()));
    }

    /**
     * Malformed JSON, a value of the wrong type (e.g. a text as player count) or a malformed id in
     * the path.
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> unreadable() {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", "Requête invalide."));
    }
}
