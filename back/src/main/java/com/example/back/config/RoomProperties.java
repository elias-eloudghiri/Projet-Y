package com.example.back.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param publicBaseUrl address of the front as players reach it, used to build share links
 */
@Validated
@ConfigurationProperties("app.rooms")
public record RoomProperties(@NotBlank String publicBaseUrl) {}
