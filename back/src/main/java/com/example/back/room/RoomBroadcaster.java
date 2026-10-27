package com.example.back.room;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pushes room changes to the players following the room over STOMP ({@link #topic}). Sent only
 * after commit, so that a rolled back change is never announced.
 */
@Slf4j
@Component
public class RoomBroadcaster {

    public static final String TOPIC_PREFIX = "/topic/rooms/";

    /**
     * Broadcasts of a room are serialized, so that its subscribers never get an older list last.
     */
    private static final int LOCK_STRIPES = 64;

    private final RoomRepository repository;
    private final SimpMessagingTemplate messaging;
    private final TransactionTemplate readTransaction;
    private final Object[] locks = new Object[LOCK_STRIPES];

    /** Published by RoomService inside the transaction that changed the participants. */
    public record ParticipantsChanged(String code) {}

    /**
     * Same for every subscriber, hence no "isMe": the front recognizes its player by its colour,
     * unique in the room.
     */
    public record ParticipantEvent(
            String pseudo,
            String color,
            @JsonProperty("isGuest") boolean isGuest,
            @JsonProperty("isCreator") boolean isCreator) {}

    /**
     * @param type kind of event, for the front to dispatch on (more to come with the next stories)
     */
    public record RoomEvent(String type, List<ParticipantEvent> participants) {

        public static final String PARTICIPANTS = "PARTICIPANTS";
    }

    public RoomBroadcaster(
            RoomRepository repository,
            SimpMessagingTemplate messaging,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.messaging = messaging;
        // The committed transaction may still be bound to the thread: read in a new one.
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction.setReadOnly(true);
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    public static String topic(String code) {
        return TOPIC_PREFIX + code;
    }

    @TransactionalEventListener
    public void participantsChanged(ParticipantsChanged event) {
        // After commit, an exception would reach the caller although its change is saved: the
        // players still get the list when they reconnect or reload.
        try {
            synchronized (locks[Math.floorMod(event.code().hashCode(), LOCK_STRIPES)]) {
                List<ParticipantEvent> participants =
                        readTransaction.execute(
                                tx ->
                                        repository
                                                .findByCode(event.code())
                                                .map(RoomBroadcaster::toEvents)
                                                .orElse(null));
                if (participants != null) {
                    messaging.convertAndSend(
                            topic(event.code()),
                            new RoomEvent(RoomEvent.PARTICIPANTS, participants));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not broadcast participants of room {}", event.code(), e);
        }
    }

    private static List<ParticipantEvent> toEvents(Room room) {
        return room.getParticipants().stream()
                .map(
                        p ->
                                new ParticipantEvent(
                                        p.getPseudo(), p.getColor(), p.isGuest(), p.isCreator()))
                .toList();
    }
}
