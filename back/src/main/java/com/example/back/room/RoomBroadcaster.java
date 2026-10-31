package com.example.back.room;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pushes the state of a room to the players following it over STOMP ({@link #topic}). Sent only
 * after commit, so that a rolled back change is never announced.
 */
@Slf4j
@Component
public class RoomBroadcaster {

    public static final String TOPIC_PREFIX = "/topic/rooms/";

    private static final Pattern ROOM_TOPIC =
            Pattern.compile(
                    Pattern.quote(TOPIC_PREFIX) + "(" + RoomCodeGenerator.CODE_PATTERN + ")");

    /**
     * Broadcasts of a room are serialized, so that its subscribers never get an older state last.
     */
    private static final int LOCK_STRIPES = 64;

    private final RoomRepository repository;
    private final RoomPresence presence;
    private final SimpMessagingTemplate messaging;
    private final TransactionTemplate readTransaction;
    private final Object[] locks = new Object[LOCK_STRIPES];

    /**
     * Published inside the transaction that changed the room (RoomService, RoomBoardService), or
     * outside any transaction by RoomPresence.
     */
    public record RoomChanged(String code) {}

    /**
     * @param type kind of event, for the front to dispatch on (more to come with the next stories)
     */
    public record RoomEvent(String type, RoomState state) {

        public static final String STATE = "STATE";
    }

    public RoomBroadcaster(
            RoomRepository repository,
            RoomPresence presence,
            SimpMessagingTemplate messaging,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.presence = presence;
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

    /** The room code of a room topic, empty for any other destination. */
    public static Optional<String> roomCode(String destination) {
        Matcher topic = ROOM_TOPIC.matcher(destination == null ? "" : destination);
        return topic.matches() ? Optional.of(topic.group(1)) : Optional.empty();
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void roomChanged(RoomChanged event) {
        // After commit, an exception would reach the caller although its change is saved: the
        // players still get the state when they reconnect or reload.
        try {
            synchronized (locks[Math.floorMod(event.code().hashCode(), LOCK_STRIPES)]) {
                // Read under the lock: the last broadcast always carries the latest state.
                RoomState state =
                        readTransaction.execute(
                                tx ->
                                        repository
                                                .findByCode(event.code())
                                                .map(
                                                        room ->
                                                                RoomState.of(
                                                                        room,
                                                                        presence.connected(
                                                                                event.code())))
                                                .orElse(null));
                if (state != null) {
                    messaging.convertAndSend(
                            topic(event.code()), new RoomEvent(RoomEvent.STATE, state));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not broadcast state of room {}", event.code(), e);
        }
    }
}
