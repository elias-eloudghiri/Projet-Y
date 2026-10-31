package com.example.back.room;

import com.example.back.room.RoomService.Player;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attribution board (US-06): who plays which character, and the start of the reading. The server is
 * the source of truth: every change is checked and saved under a lock on the room row, then
 * broadcast once committed (see RoomBroadcaster).
 */
@Service
@RequiredArgsConstructor
public class RoomBoardService {

    private final RoomRepository repository;
    private final RoomPresence presence;
    private final ApplicationEventPublisher events;

    /**
     * @param meId the caller's participant id, for the front to recognize its player
     */
    public record Board(UUID meId, RoomState state) {}

    /** Full state, reserved to participants (404 otherwise, like {@link RoomService}). */
    @Transactional(readOnly = true)
    public Board get(String code, Player player) {
        Room room = repository.findByCode(code).orElseThrow(RoomException::notFound);
        RoomParticipant me =
                room.findParticipant(player.subject(), player.guest())
                        .orElseThrow(RoomException::notFound);
        return new Board(me.getId(), state(room));
    }

    /**
     * Gives a character to a player of the room. Reserved to the creator; does not depend on the
     * room status, so that it can also be used during the reading. Giving a character to its
     * current player changes nothing and is not broadcast.
     */
    @Transactional
    public RoomState assign(String code, UUID characterId, UUID participantId, Player player) {
        Room room = lockAsCreator(code, player);
        // The attribution holds one entry per character of the story.
        if (!room.getAssignments().containsKey(characterId)) {
            throw RoomException.characterNotFound();
        }
        if (participantId == null) {
            throw RoomException.invalid("Choisis un joueur.");
        }
        if (room.findParticipant(participantId).isEmpty()) {
            throw RoomException.playerNotFound();
        }
        if (room.assign(characterId, participantId)) {
            changed(room);
        }
        return state(room);
    }

    /**
     * Starts the reading: reserved to the creator, once the planned number of players is reached
     * and each of them has a character.
     */
    @Transactional
    public RoomState start(String code, Player player) {
        Room room = lockAsCreator(code, player);
        if (room.getStatus() != RoomStatus.WAITING) {
            throw RoomException.alreadyStarted();
        }
        if (room.getParticipants().size() < room.getPlayerCount()) {
            throw RoomException.notReady("Tous les joueurs ne sont pas encore arrivés.");
        }
        if (!room.isReadyToStart()) {
            throw RoomException.notReady("Chaque joueur doit avoir au moins un personnage.");
        }
        room.start();
        changed(room);
        return state(room);
    }

    /** Non-participants get a 404, as for reading the room; other players a 403. */
    private Room lockAsCreator(String code, Player player) {
        Room room = repository.lockByCode(code).orElseThrow(RoomException::notFound);
        RoomParticipant me =
                room.findParticipant(player.subject(), player.guest())
                        .orElseThrow(RoomException::notFound);
        if (!me.isCreator()) {
            throw RoomException.notCreator();
        }
        return room;
    }

    private void changed(Room room) {
        room.stateChanged();
        events.publishEvent(new RoomBroadcaster.RoomChanged(room.getCode()));
    }

    private RoomState state(Room room) {
        return RoomState.of(room, presence.connected(room.getCode()));
    }
}
