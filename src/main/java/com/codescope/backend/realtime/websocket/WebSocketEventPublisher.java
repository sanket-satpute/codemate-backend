package com.codescope.backend.realtime.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishToProject(String projectId, WebSocketEventPayload payload) {
        messagingTemplate.convertAndSend("/topic/jobs/" + projectId, payload);
    }

    public void publishToJob(String projectId, String jobId, WebSocketEventPayload payload) {
        messagingTemplate.convertAndSend("/topic/jobs/" + projectId + "/" + jobId, payload);
    }

    public void publishNotification(WebSocketEventPayload payload) {
        messagingTemplate.convertAndSend("/topic/notifications", payload);
    }
}
