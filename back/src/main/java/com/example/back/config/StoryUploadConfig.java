package com.example.back.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class StoryUploadConfig {

    /** Room left in a request for the story metadata sent next to the PDF. */
    private static final DataSize METADATA_ALLOWANCE = DataSize.ofMegabytes(1);

    /**
     * Multipart limits follow STORY_PDF_MAX_SIZE, so a single variable sets the upload limit.
     * Replaces Spring Boot's spring.servlet.multipart.* defaults (1 MB per file).
     */
    @Bean
    MultipartConfigElement multipartConfigElement(StoryProperties properties) {
        long maxFile = properties.pdfMaxSize().toBytes();
        return new MultipartConfigElement("", maxFile, maxFile + METADATA_ALLOWANCE.toBytes(), 0);
    }
}
