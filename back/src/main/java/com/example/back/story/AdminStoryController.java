package com.example.back.story;

import com.example.back.config.StoryProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Story catalogue management, reserved to administrators (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin/stories")
@RequiredArgsConstructor
public class AdminStoryController {

    private final StoryService storyService;
    private final StoryProperties properties;

    public record StoryResponse(
            UUID id,
            String title,
            String description,
            int maxPlayers,
            List<String> characters,
            long pdfSize,
            Instant createdAt) {
        static StoryResponse of(Story story) {
            return new StoryResponse(
                    story.getId(),
                    story.getTitle(),
                    story.getDescription(),
                    story.getMaxPlayers(),
                    story.getCharacters().stream().map(StoryCharacter::getName).toList(),
                    story.getPdfSize(),
                    story.getCreatedAt());
        }
    }

    public record UploadLimitsResponse(long pdfMaxSize) {}

    /**
     * Multipart request: a "story" JSON part ({@link StoryService.NewStory}) and a "pdf" file part.
     * The PDF part is optional here so that its absence gets a clear message.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StoryResponse create(
            @RequestPart("story") StoryService.NewStory story,
            @RequestPart(name = "pdf", required = false) MultipartFile pdf,
            @AuthenticationPrincipal Jwt jwt) {
        return StoryResponse.of(storyService.create(story, pdf, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping
    public List<StoryResponse> list() {
        return storyService.list().stream().map(StoryResponse::of).toList();
    }

    /** Lets the form check the PDF size before sending it. */
    @GetMapping("/limits")
    public UploadLimitsResponse limits() {
        return new UploadLimitsResponse(properties.pdfMaxSize().toBytes());
    }
}
