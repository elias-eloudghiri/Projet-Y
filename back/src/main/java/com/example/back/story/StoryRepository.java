package com.example.back.story;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, UUID> {

    boolean existsByTitleIgnoreCase(String title);

    @EntityGraph(attributePaths = "characters")
    List<Story> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "characters")
    Optional<Story> findWithCharactersById(UUID id);
}
