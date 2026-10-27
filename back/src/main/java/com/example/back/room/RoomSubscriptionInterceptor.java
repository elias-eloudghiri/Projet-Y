package com.example.back.room;

import com.example.back.room.RoomService.Player;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Guards the STOMP frames sent by clients. The handshake already went through Spring Security (JWT
 * cookie), so every session has a principal; on top of that, a room topic can only be followed by
 * its participants, and clients cannot publish anything.
 */
@Component
@RequiredArgsConstructor
public class RoomSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC =
            Pattern.compile(
                    Pattern.quote(RoomBroadcaster.TOPIC_PREFIX)
                            + "("
                            + RoomCodeGenerator.CODE_PATTERN
                            + ")");

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
            String destination = accessor.getDestination();
            Matcher topic = ROOM_TOPIC.matcher(destination == null ? "" : destination);
            Player player = player(accessor.getUser());
            if (!topic.matches()
                    || player == null
                    || !roomService.isParticipant(topic.group(1), player)) {
                throw new MessageDeliveryException(message, "ROOM_FORBIDDEN");
            }
        }
        return message;
    }

    private static Player player(Principal user) {
        return user instanceof JwtAuthenticationToken token ? Player.from(token.getToken()) : null;
    }
}
