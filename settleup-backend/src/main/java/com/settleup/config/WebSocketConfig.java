package com.settleup.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket (STOMP over SockJS) configuration.
 *
 * - Endpoint: /ws  (SockJS fallback enabled for browsers without native WS)
 * - Client subscribes to topics under /topic/...
 * - Application destination prefix: /app (for client → server messages, Phase 2+)
 *
 * Topics pushed by the server (Phase 2):
 *   /topic/group/{groupId}/balances   — balance updates on expense / settlement
 *   /topic/group/{groupId}/expenses   — new expense added
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker for /topic and /queue destinations
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefix for messages routed to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // Restrict in production
                .withSockJS();
    }
}
