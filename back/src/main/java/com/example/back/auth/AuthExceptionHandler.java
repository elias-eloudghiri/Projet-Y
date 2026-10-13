package com.example.back.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_CREDENTIALS", "Pseudo ou mot de passe incorrect."));
    }

    @ExceptionHandler(AuthService.TooManyAttemptsException.class)
    ResponseEntity<ApiError> tooManyAttempts(AuthService.TooManyAttemptsException e) {
        long seconds = Math.max(1, e.retryAfter().toSeconds());
        long minutes = (seconds + 59) / 60;
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds))
                .body(new ApiError("TOO_MANY_ATTEMPTS",
                        "Trop de tentatives. Réessaie dans " + minutes + " min."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException e) {
        boolean passwordLength = e.getBindingResult().getFieldErrors().stream()
                .anyMatch(f -> f.getField().equals("password") && "Size".equals(f.getCode()));
        String message = passwordLength
                ? "Le mot de passe doit contenir entre " + AuthController.PASSWORD_MIN_LENGTH
                        + " et " + AuthController.PASSWORD_MAX_LENGTH + " caractères."
                : "Requête invalide.";
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", message));
    }
}
