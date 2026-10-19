package com.example.back.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** S3-compatible object storage: SeaweedFS in dev, any S3 provider in prod. */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
        @NotNull URI endpoint,
        @NotBlank String region,
        @NotBlank(message = "S3_ACCESS_KEY must be set") String accessKey,
        @NotBlank(message = "S3_SECRET_KEY must be set") String secretKey,
        @NotBlank String bucket) {}
