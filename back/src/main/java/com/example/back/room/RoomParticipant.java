package com.example.back.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A player of a room: an account (user id) or a guest (guest id, only known from its JWT). */
@Entity
@Table(name = "room_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "guest_id")
    private UUID guestId;

    @Column(nullable = false, length = 32)
    private String pseudo;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(name = "is_creator", nullable = false)
    private boolean creator;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    RoomParticipant(
            Room room,
            UUID userId,
            UUID guestId,
            String pseudo,
            String color,
            boolean creator,
            Instant joinedAt) {
        this.room = room;
        this.userId = userId;
        this.guestId = guestId;
        this.pseudo = pseudo;
        this.color = color;
        this.creator = creator;
        this.joinedAt = joinedAt;
    }
}
