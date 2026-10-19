package com.example.back.story;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Story catalogue as seen by players (accounts and guests): no storage details. */
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    public record CatalogStoryResponse(
            UUID id, String title, String description, int maxPlayers, List<String> characters) {
        static CatalogStoryResponse of(Story story) {
            return new CatalogStoryResponse(
                    story.getId(),
                    story.getTitle(),
                    story.getDescription(),
                    story.getMaxPlayers(),
                    story.getCharacters().stream().map(StoryCharacter::getName).toList());
        }
    }

    @GetMapping
    public List<CatalogStoryResponse> list() {
        return storyService.list().stream().map(CatalogStoryResponse::of).toList();
    }

    @GetMapping("/{id}")
    public CatalogStoryResponse get(@PathVariable UUID id) {
        return CatalogStoryResponse.of(storyService.get(id));
    }
}
