package com.example.back.room;

import com.example.back.room.RoomService.Player;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Which players have their room open: a player is connected while at least one of its STOMP
 * sessions (tabs) follows the room topic. Dead connections are noticed through the heartbeats.
 *
 * <p>In memory, like the broker: per application instance, empty after a restart until clients
 * reconnect. Each change of a player's presence is broadcast to the room.
 */
@Component
@RequiredArgsConstructor
public class RoomPresence {

    private final RoomService roomService;
    private final ApplicationEventPublisher events;

    private record Seat(String code, UUID participantId) {}

    /** Subscriptions to room topics, by STOMP session then subscription id. */
    private final Map<String, Map<String, Seat>> sessions = new HashMap<>();

    /** Number of subscriptions of each player. */
    private final Map<Seat, Integer> counts = new HashMap<>();

    /** Participant ids of the connected players of a room. */
    public synchronized Set<UUID> connected(String code) {
        return counts.keySet().stream()
                .filter(seat -> seat.code().equals(code))
                .map(Seat::participantId)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Only reached by subscriptions accepted by RoomSubscriptionInterceptor. */
    @EventListener
    public void subscribed(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Optional<String> code = RoomBroadcaster.roomCode(accessor.getDestination());
        Player player = Player.from(event.getUser());
        if (code.isEmpty() || player == null || accessor.getSubscriptionId() == null) {
            return;
        }
        Optional<UUID> participantId = roomService.findParticipantId(code.get(), player);
        if (participantId.isEmpty()) {
            return;
        }
        Seat seat = new Seat(code.get(), participantId.get());
        boolean arrived;
        synchronized (this) {
            sessions.computeIfAbsent(accessor.getSessionId(), s -> new HashMap<>())
                    .put(accessor.getSubscriptionId(), seat);
            arrived = counts.merge(seat, 1, Integer::sum) == 1;
        }
        if (arrived) {
            changed(seat);
        }
    }

    @EventListener
    public void unsubscribed(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Seat left = null;
        synchronized (this) {
            Map<String, Seat> subscriptions = sessions.get(accessor.getSessionId());
            Seat seat =
                    subscriptions == null
                            ? null
                            : subscriptions.remove(accessor.getSubscriptionId());
            if (seat != null && release(seat)) {
                left = seat;
            }
        }
        if (left != null) {
            changed(left);
        }
    }

    /** May be received more than once for a session. */
    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        List<Seat> left;
        synchronized (this) {
            Map<String, Seat> subscriptions = sessions.remove(event.getSessionId());
            left =
                    subscriptions == null
                            ? List.of()
                            : subscriptions.values().stream().filter(this::release).toList();
        }
        left.stream().distinct().forEach(this::changed);
    }

    /**
     * @return whether it was the player's last subscription
     */
    private boolean release(Seat seat) {
        return counts.computeIfPresent(seat, (s, n) -> n > 1 ? n - 1 : null) == null;
    }

    private void changed(Seat seat) {
        events.publishEvent(new RoomBroadcaster.RoomChanged(seat.code()));
    }
}
