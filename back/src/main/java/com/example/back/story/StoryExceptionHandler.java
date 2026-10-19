package com.example.back.story;

import com.example.back.auth.AuthExceptionHandler.ApiError;
import com.example.back.config.StoryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@RequiredArgsConstructor
public class StoryExceptionHandler {

    private final StoryProperties properties;

    @ExceptionHandler(StoryValidationException.class)
    ResponseEntity<ApiError> invalidStory(StoryValidationException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage()));
    }

    /** Request larger than the multipart limits, rejected before reaching the controller. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge() {
        return invalidStory(StoryService.tooLarge(properties.pdfMaxSize().toBytes()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missingPart() {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_REQUEST", "Requête invalide."));
    }
}
