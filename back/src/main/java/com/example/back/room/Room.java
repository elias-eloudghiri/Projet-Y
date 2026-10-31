package com.example.back.room;

import com.example.back.story.Story;
import com.example.back.story.StoryCharacter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Increased on every change of the shared state (players, attribution, status). */
    @Column(name = "state_version", nullable = false)
    private long stateVersion;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("joinedAt, id")
    private List<RoomParticipant> participants = new ArrayList<>();

    /** Player (participant id) of each character (character id) of the story. */
    @ElementCollection
    @CollectionTable(name = "room_assignments", joinColumns = @JoinColumn(name = "room_id"))
    @MapKeyColumn(name = "character_id")
    @Column(name = "participant_id", nullable = false)
    private Map<UUID, UUID> assignments = new HashMap<>();

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

    public Map<UUID, UUID> getAssignments() {
        return Collections.unmodifiableMap(assignments);
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

    /** A participant by its own id, the one exposed to the front. */
    public Optional<RoomParticipant> findParticipant(UUID participantId) {
        return participants.stream().filter(p -> p.getId().equals(participantId)).findFirst();
    }

    /** Character ids of the story, in story order. */
    public List<UUID> characterIds() {
        return story.getCharacters().stream().map(StoryCharacter::getId).toList();
    }

    /** Gives every character to the creator. Called once the creator is saved: its id is needed. */
    void assignAllToCreator() {
        UUID creator = participants.getFirst().getId();
        characterIds().forEach(c -> assignments.put(c, creator));
    }

    /** Hands characters to a player who just joined, already saved (see AssignmentBalancer). */
    void balanceFor(RoomParticipant newcomer) {
        AssignmentBalancer.giveToNewcomer(
                        characterIds(),
                        assignments,
                        participants.stream().map(RoomParticipant::getId).toList(),
                        newcomer.getId())
                // Only the entries that changed are written.
                .forEach(this::assign);
    }

    /**
     * Gives a character to a player, both checked by the caller.
     *
     * @return whether anything changed
     */
    boolean assign(UUID characterId, UUID participantId) {
        if (participantId.equals(assignments.get(characterId))) {
            return false;
        }
        assignments.put(characterId, participantId);
        return true;
    }

    long characterCount(RoomParticipant participant) {
        return assignments.values().stream().filter(participant.getId()::equals).count();
    }

    /** The planned number of players is reached and each of them has a character. */
    public boolean isReadyToStart() {
        return participants.size() == playerCount
                && participants.stream().allMatch(p -> characterCount(p) > 0);
    }

    void start() {
        status = RoomStatus.PLAYING;
    }

    /** To be called by every change that is broadcast to the players. */
    void stateChanged() {
        stateVersion++;
    }
}
