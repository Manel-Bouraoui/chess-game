// Spring WebSocket + STOMP configuration for real-time game updates

package com.chess.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure the message broker for routing messages between clients
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory message broker
        // For production, use RabbitMQ or ActiveMQ
        config
                .enableSimpleBroker("/topic", "/queue");

        // Set prefix for endpoints where clients send messages
        config.setApplicationDestinationPrefixes("/app");

        // User destination prefix for private messages
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register STOMP endpoints for WebSocket connections
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000")
                .withSockJS(); // Fallback for browsers without WebSocket support

        // Add native WebSocket endpoint (alternative)
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000");
    }
}
