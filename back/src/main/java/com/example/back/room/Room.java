package com.example.back.room;

import com.example.back.story.Story;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Public identifier, used in the share link; the internal id is never exposed. */
    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Capacity chosen by the creator, between 2 and the story maximum. */
    @Column(name = "player_count", nullable = false)
    private int playerCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinedAt, id")
    private List<RoomParticipant> participants = new ArrayList<>();

    public Room(
            String code,
            Story story,
            UUID creatorId,
            String passwordHash,
            int playerCount,
            Instant createdAt) {
        this.code = code;
        this.story = story;
        this.creatorId = creatorId;
        this.passwordHash = passwordHash;
        this.playerCount = playerCount;
        this.status = RoomStatus.WAITING;
        this.createdAt = createdAt;
    }

    public List<RoomParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    /** The creator is the first participant, with the first colour of the palette. */
    public void addCreator(UUID userId, String pseudo) {
        participants.add(
                new RoomParticipant(
                        this, userId, null, pseudo, RoomColor.PALETTE.getFirst(), true, createdAt));
    }

    /**
     * Adds a player with the first colour of the palette not taken in the room. Capacity and status
     * are checked by the caller, which holds a lock on the room.
     */
    public RoomParticipant addPlayer(UUID subject, boolean guest, String pseudo, Instant joinedAt) {
        List<String> taken = participants.stream().map(RoomParticipant::getColor).toList();
        String color =
                RoomColor.PALETTE.stream()
                        .filter(c -> !taken.contains(c))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No colour left in room"));
        RoomParticipant participant =
                new RoomParticipant(
                        this,
                        guest ? null : subject,
                        guest ? subject : null,
                        pseudo,
                        color,
                        false,
                        joinedAt);
        participants.add(participant);
        return participant;
    }

    public boolean isFull() {
        return participants.size() >= playerCount;
    }

    /** The participant of an access token identity (user id, or guest id for guests). */
    public Optional<RoomParticipant> findParticipant(UUID subject, boolean guest) {
        return participants.stream()
                .filter(p -> subject.equals(guest ? p.getGuestId() : p.getUserId()))
                .findFirst();
    }

    public boolean hasParticipant(UUID subject, boolean guest) {
        return findParticipant(subject, guest).isPresent();
    }
}
