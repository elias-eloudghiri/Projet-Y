package com.example.back.story;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A role of a story. The narrator is a character like the others, always at position 0. */
@Entity
@Table(name = "story_characters")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoryCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_narrator", nullable = false)
    private boolean narrator;

    StoryCharacter(Story story, String name, int position, boolean narrator) {
        this.story = story;
        this.name = name;
        this.position = position;
        this.narrator = narrator;
    }
}
