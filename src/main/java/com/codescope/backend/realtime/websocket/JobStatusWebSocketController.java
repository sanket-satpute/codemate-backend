package com.codescope.backend.realtime.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST endpoint for job status updates.
 * Replaces the servlet-based @MessageMapping STOMP controller.
 * Server-pushed job updates are delivered via reactive WebSocket topics.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobStatusWebSocketController {

    private final WebSocketEventPublisher webSocketEventPublisher;

    @PostMapping("/status")
    public Mono<Void> handleJobStatusUpdate(@RequestBody WebSocketEventPayload payload) {
        log.info("Received job status update: {}", payload);
        // Broadcast to WebSocket subscribers if needed
        if (payload.getProjectId() != null) {
            webSocketEventPublisher.publishToProject(payload.getProjectId(), payload);
        }
        return Mono.empty();
    }
}
