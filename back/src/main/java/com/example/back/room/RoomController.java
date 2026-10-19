package com.example.back.room;

import com.example.back.auth.AccessTokenService;
import com.example.back.config.RoomProperties;
import com.example.back.user.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rooms, identified by their public code. Creation is reserved to accounts (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomProperties properties;

    public record StorySummary(UUID id, String title, int maxPlayers) {}

    public record ParticipantResponse(
            String pseudo, String color, @JsonProperty("isCreator") boolean isCreator) {}

    /** Never carries the password hash nor any internal id besides the story one. */
    public record RoomResponse(
            String code,
            String shareUrl,
            StorySummary story,
            int playerCount,
            RoomStatus status,
            List<ParticipantResponse> participants) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(
            @RequestBody RoomService.NewRoom room, @AuthenticationPrincipal Jwt jwt) {
        return toResponse(roomService.create(room, UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/{code}")
    public RoomResponse get(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        boolean guest =
                Role.GUEST.name().equals(jwt.getClaimAsString(AccessTokenService.CLAIM_ROLE));
        return toResponse(
                roomService.getForParticipant(
                        code.toUpperCase(Locale.ROOT), UUID.fromString(jwt.getSubject()), guest));
    }

    private RoomResponse toResponse(Room room) {
        return new RoomResponse(
                room.getCode(),
                shareUrl(room.getCode()),
                new StorySummary(
                        room.getStory().getId(),
                        room.getStory().getTitle(),
                        room.getStory().getMaxPlayers()),
                room.getPlayerCount(),
                room.getStatus(),
                room.getParticipants().stream()
                        .map(
                                p ->
                                        new ParticipantResponse(
                                                p.getPseudo(), p.getColor(), p.isCreator()))
                        .toList());
    }

    private String shareUrl(String code) {
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/room/" + code;
    }
}
