package com.codescope.backend.realtime.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class JobStatusWebSocketController {

    private final WebSocketEventPublisher webSocketEventPublisher;

    @MessageMapping("/job/status")
    public void handleJobStatusUpdate(WebSocketEventPayload payload) {
        // This endpoint is primarily for receiving status updates if needed,
        // but most events are server-pushed.
        // For now, we can just log the received payload.
        System.out.println("Received job status update: " + payload);
    }
}
