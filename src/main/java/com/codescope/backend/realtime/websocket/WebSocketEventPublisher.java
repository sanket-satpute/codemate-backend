package com.codescope.backend.realtime.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Publishes events to WebSocket subscribers via the reactive WebSocket handler.
 * Replaces the servlet-based SimpMessagingTemplate approach.
 */
@Service
@RequiredArgsConstructor
public class WebSocketEventPublisher {

    private final ReactiveWebSocketHandler webSocketHandler;

    public void publishToProject(String projectId, WebSocketEventPayload payload) {
        webSocketHandler.broadcast("/topic/jobs/" + projectId, payload);
    }

    public void publishToJob(String projectId, String jobId, WebSocketEventPayload payload) {
        webSocketHandler.broadcast("/topic/jobs/" + projectId + "/" + jobId, payload);
    }

    public void publishNotification(WebSocketEventPayload payload) {
        webSocketHandler.broadcast("/topic/notifications", payload);
    }
}
