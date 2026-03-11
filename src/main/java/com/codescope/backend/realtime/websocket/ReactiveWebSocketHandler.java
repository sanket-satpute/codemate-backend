package com.codescope.backend.realtime.websocket;

import com.codescope.backend.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Reactive WebSocket handler that replaces the servlet-based STOMP/SockJS stack.
 *
 * Protocol (JSON over native WebSocket):
 *   Client → Server:  { "type": "SUBSCRIBE", "topic": "/topic/notifications" }
 *   Client → Server:  { "type": "SEND", "destination": "/app/chat/...", "body": { ... } }
 *   Server → Client:  { "topic": "/topic/notifications", "payload": { ... } }
 *
 * Authentication: Pass JWT as query param ?token=xxx on the ws:// URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReactiveWebSocketHandler implements WebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final ReactiveUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    /**
     * topic → set of per-session sinks (multicast to all subscribers of a topic).
     */
    private final Map<String, Set<Sinks.Many<String>>> topicSubscribers = new ConcurrentHashMap<>();

    /**
     * sessionId → set of topics that session subscribed to (for cleanup on disconnect).
     */
    private final Map<String, Set<String>> sessionTopics = new ConcurrentHashMap<>();

    /**
     * sessionId → sink (for sending messages to a specific session, e.g. targeted events).
     */
    private final Map<String, Sinks.Many<String>> sessionSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Extract token from query param: ws://host/ws?token=JWT
        String query = session.getHandshakeInfo().getUri().getQuery();
        String token = extractToken(query);

        if (token == null || token.isEmpty()) {
            log.warn("WebSocket connection rejected: no token provided");
            return session.close();
        }

        // Validate JWT
        String username;
        try {
            username = jwtTokenProvider.extractUsername(token);
        } catch (Exception e) {
            log.warn("WebSocket connection rejected: invalid token");
            return session.close();
        }

        return userDetailsService.findByUsername(username)
                .flatMap(userDetails -> {
                    if (!jwtTokenProvider.validateToken(token, userDetails)) {
                        log.warn("WebSocket connection rejected: token validation failed for {}", username);
                        return session.close();
                    }

                    log.info("WebSocket connected: user={}, sessionId={}", username, session.getId());

                    // Create a sink for this session's outbound messages
                    Sinks.Many<String> outbound = Sinks.many().multicast().onBackpressureBuffer();
                    sessionSinks.put(session.getId(), outbound);
                    sessionTopics.put(session.getId(), ConcurrentHashMap.newKeySet());

                    // Outbound: send queued messages to client
                    Mono<Void> output = session.send(
                            outbound.asFlux().map(session::textMessage)
                    );

                    // Inbound: handle SUBSCRIBE / SEND messages from client
                    Mono<Void> input = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnNext(msg -> handleClientMessage(session.getId(), msg, outbound))
                            .doFinally(sig -> cleanup(session.getId()))
                            .then();

                    return Mono.zip(input, output).then();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("WebSocket connection rejected: user not found");
                    return session.close();
                }));
    }

    /**
     * Handle a JSON message from the client.
     */
    private void handleClientMessage(String sessionId, String rawMessage, Sinks.Many<String> outbound) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(rawMessage, Map.class);
            String type = (String) msg.get("type");

            if ("SUBSCRIBE".equals(type)) {
                String topic = (String) msg.get("topic");
                if (topic != null) {
                    topicSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(outbound);
                    sessionTopics.get(sessionId).add(topic);
                    log.debug("Session {} subscribed to {}", sessionId, topic);
                }
            } else if ("UNSUBSCRIBE".equals(type)) {
                String topic = (String) msg.get("topic");
                if (topic != null) {
                    Set<Sinks.Many<String>> subs = topicSubscribers.get(topic);
                    if (subs != null) {
                        subs.remove(outbound);
                    }
                    Set<String> topics = sessionTopics.get(sessionId);
                    if (topics != null) {
                        topics.remove(topic);
                    }
                }
            } else if ("SEND".equals(type)) {
                // Client sending a message to a destination (e.g. /app/chat/...)
                // For now, log it. Extend as needed.
                log.debug("Client SEND from session {}: {}", sessionId, rawMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to parse client WebSocket message: {}", rawMessage, e);
        }
    }

    /**
     * Publish a message to all subscribers of a given topic.
     * Called by WebSocketEventPublisher.
     */
    public void broadcast(String topic, Object payload) {
        Set<Sinks.Many<String>> subs = topicSubscribers.get(topic);
        if (subs == null || subs.isEmpty()) {
            return;
        }

        String json;
        try {
            Map<String, Object> envelope = Map.of("topic", topic, "payload", payload);
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebSocket broadcast payload", e);
            return;
        }

        for (Sinks.Many<String> sink : subs) {
            sink.tryEmitNext(json);
        }
    }

    /**
     * Cleanup when a session disconnects.
     */
    private void cleanup(String sessionId) {
        log.info("WebSocket session disconnected: {}", sessionId);

        Sinks.Many<String> sink = sessionSinks.remove(sessionId);
        Set<String> topics = sessionTopics.remove(sessionId);

        if (topics != null && sink != null) {
            for (String topic : topics) {
                Set<Sinks.Many<String>> subs = topicSubscribers.get(topic);
                if (subs != null) {
                    subs.remove(sink);
                    if (subs.isEmpty()) {
                        topicSubscribers.remove(topic);
                    }
                }
            }
        }

        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    private String extractToken(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
