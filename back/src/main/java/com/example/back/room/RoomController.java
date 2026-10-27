package com.example.back.room;

import com.example.back.config.RoomProperties;
import com.example.back.room.RoomService.Player;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
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
 * Rooms, identified by their public code. Creation is reserved to accounts (see SecurityConfig);
 * accounts and guests may join.
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomProperties properties;

    public record StorySummary(UUID id, String title, int maxPlayers) {}

    /**
     * @param isMe whether this participant is the caller
     */
    public record ParticipantResponse(
            String pseudo,
            String color,
            @JsonProperty("isGuest") boolean isGuest,
            @JsonProperty("isCreator") boolean isCreator,
            @JsonProperty("isMe") boolean isMe) {}

    /** Never carries the password hash nor any internal id besides the story one. */
    public record RoomResponse(
            String code,
            String shareUrl,
            StorySummary story,
            int playerCount,
            RoomStatus status,
            List<ParticipantResponse> participants) {}

    public record JoinRequest(String password) {}

    /**
     * @param participant the caller's participation
     */
    public record JoinResponse(
            ParticipantResponse participant, List<ParticipantResponse> participants) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(
            @RequestBody RoomService.NewRoom room, @AuthenticationPrincipal Jwt jwt) {
        Player player = Player.from(jwt);
        return toResponse(roomService.create(room, player.subject()), player);
    }

    @GetMapping("/{code}")
    public RoomResponse get(@PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        Player player = Player.from(jwt);
        return toResponse(roomService.getForParticipant(normalize(code), player), player);
    }

    /** Reserved to participants; also tells the front whether the caller is already in. */
    @GetMapping("/{code}/participants")
    public List<ParticipantResponse> participants(
            @PathVariable String code, @AuthenticationPrincipal Jwt jwt) {
        Player player = Player.from(jwt);
        return toParticipants(roomService.getParticipants(normalize(code), player), player);
    }

    @PostMapping("/{code}/join")
    public JoinResponse join(
            @PathVariable String code,
            @RequestBody JoinRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest http) {
        Player player = Player.from(jwt);
        RoomService.JoinResult result =
                roomService.join(normalize(code), request.password(), player, http.getRemoteAddr());
        return new JoinResponse(
                toParticipant(result.participant(), player), toParticipants(result.room(), player));
    }

    private static String normalize(String code) {
        return code.toUpperCase(Locale.ROOT);
    }

    private RoomResponse toResponse(Room room, Player player) {
        return new RoomResponse(
                room.getCode(),
                shareUrl(room.getCode()),
                new StorySummary(
                        room.getStory().getId(),
                        room.getStory().getTitle(),
                        room.getStory().getMaxPlayers()),
                room.getPlayerCount(),
                room.getStatus(),
                toParticipants(room, player));
    }

    private static List<ParticipantResponse> toParticipants(Room room, Player player) {
        return room.getParticipants().stream().map(p -> toParticipant(p, player)).toList();
    }

    private static ParticipantResponse toParticipant(RoomParticipant p, Player player) {
        UUID id = player.guest() ? p.getGuestId() : p.getUserId();
        return new ParticipantResponse(
                p.getPseudo(),
                p.getColor(),
                p.isGuest(),
                p.isCreator(),
                player.subject().equals(id));
    }

    private String shareUrl(String code) {
        return properties.publicBaseUrl().replaceAll("/+$", "") + "/room/" + code;
    }
}
