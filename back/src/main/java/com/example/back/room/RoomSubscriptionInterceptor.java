package com.example.back.room;

import com.example.back.room.RoomService.Player;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Guards the STOMP frames sent by clients. The handshake already went through Spring Security (JWT
 * cookie), so every session has a principal; on top of that, a room topic can only be followed by
 * its participants, and clients cannot publish anything: commands go through the REST API.
 */
@Component
@RequiredArgsConstructor
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private final RoomService roomService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.SEND) {
            throw new MessageDeliveryException(message, "Sending is not allowed");
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            Optional<String> code = RoomBroadcaster.roomCode(accessor.getDestination());
            Player player = Player.from(accessor.getUser());
            if (code.isEmpty()
                    || player == null
                    || !roomService.isParticipant(code.get(), player)) {
                throw new MessageDeliveryException(message, "ROOM_FORBIDDEN");
            }
        }
        return message;
    }
}
