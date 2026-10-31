package com.example.back.room;

import com.example.back.story.StoryCharacter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared state of a room: players, attribution board and status. Same for every player, hence no
 * "isMe": pushed over STOMP and returned by the API.
 *
 * @param version {@link Room#getStateVersion()}: a client drops a state older than the one it
 *     displays. Presence changes are not saved, so they are sent with an unchanged version.
 */
public record RoomState(
        long version,
        RoomStatus status,
        int playerCount,
        List<PlayerState> players,
        List<CharacterState> characters) {

    /**
     * @param id participant id, used to target a player; neither a user id nor a guest id
     * @param connected whether the player has the room open (see RoomPresence)
     */
    public record PlayerState(
            UUID id,
            String pseudo,
            String color,
            @JsonProperty("isGuest") boolean isGuest,
            @JsonProperty("isCreator") boolean isCreator,
            boolean connected) {}

    /** In story order, the narrator first. */
    public record CharacterState(
            UUID id, String name, @JsonProperty("isNarrator") boolean isNarrator, UUID playerId) {}

    /** To be called in a transaction: the story characters and the attribution are lazy. */
    static RoomState of(Room room, Set<UUID> connected) {
        List<PlayerState> players =
                room.getParticipants().stream()
                        .map(
                                p ->
                                        new PlayerState(
                                                p.getId(),
                                                p.getPseudo(),
                                                p.getColor(),
                                                p.isGuest(),
                                                p.isCreator(),
                                                connected.contains(p.getId())))
                        .toList();
        List<CharacterState> characters =
                room.getStory().getCharacters().stream()
                        .map(c -> character(c, room.getAssignments().get(c.getId())))
                        .toList();
        return new RoomState(
                room.getStateVersion(),
                room.getStatus(),
                room.getPlayerCount(),
                players,
                characters);
    }

    private static CharacterState character(StoryCharacter c, UUID playerId) {
        return new CharacterState(c.getId(), c.getName(), c.isNarrator(), playerId);
    }
}
