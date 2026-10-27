package com.example.back.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param publicBaseUrl address of the front as players reach it, used to build share links
 * @param joinMaxFailures wrong room passwords allowed per (IP, room) within {@code
 *     joinFailureWindow}; further attempts are refused until the window ends
 */
@Validated
@ConfigurationProperties("app.rooms")
public record RoomProperties(
        @NotBlank String publicBaseUrl,
        @Positive int joinMaxFailures,
        @NotNull Duration joinFailureWindow) {}
