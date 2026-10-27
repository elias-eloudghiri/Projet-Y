package com.example.back.config;

import com.example.back.room.RoomSubscriptionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real time (STOMP over WebSocket, no SockJS fallback). The handshake on {@link #ENDPOINT} is an
 * ordinary HTTP request, authenticated by the JWT cookie like the API (see SecurityConfig).
 *
 * <p>In-memory broker: enough for one instance. Several instances will need a broker relay
 * (RabbitMQ, ActiveMQ…) so that a message reaches the players connected to the other ones.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws";

    /** Lets a client and the server notice a dead connection, and keeps proxies from closing it. */
    private static final long HEARTBEAT_MS = 10_000;

    private final RoomProperties roomProperties;
    private final RoomSubscriptionInterceptor roomSubscriptionInterceptor;
    private TaskScheduler heartbeatScheduler;

    /** The broker's own scheduler, created by this very configuration: hence lazy. */
    @Autowired
    void setHeartbeatScheduler(
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler scheduler) {
        this.heartbeatScheduler = scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Origin check against cross-site WebSocket hijacking: cookies are sent with the handshake.
        registry.addEndpoint(ENDPOINT).setAllowedOrigins(roomProperties.publicBaseUrl());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler);
        // Messages of a session are delivered in the order they were published.
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(roomSubscriptionInterceptor);
    }
}
