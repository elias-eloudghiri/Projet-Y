package com.example.back.story;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Column(name = "pdf_key", nullable = false, unique = true)
    private String pdfKey;

    @Column(name = "pdf_size", nullable = false)
    private long pdfSize;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<StoryCharacter> characters = new ArrayList<>();

    public Story(
            String title,
            String description,
            int playerCount,
            String pdfKey,
            long pdfSize,
            UUID createdBy,
            Instant createdAt) {
        this.title = title;
        this.description = description;
        this.playerCount = playerCount;
        this.pdfKey = pdfKey;
        this.pdfSize = pdfSize;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public List<StoryCharacter> getCharacters() {
        return Collections.unmodifiableList(characters);
    }

    /** Appends a character at the next position. */
    public void addCharacter(String name, boolean narrator) {
        characters.add(new StoryCharacter(this, name, characters.size(), narrator));
    }
}
