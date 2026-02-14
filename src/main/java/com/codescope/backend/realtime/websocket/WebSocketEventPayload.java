package com.codescope.backend.realtime.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketEventPayload {

    private WebSocketEventType type;
    private String projectId;
    private String jobId;
    private String messageId;
    private long timestamp;
    private Object data;

    public WebSocketEventPayload(WebSocketEventType type, String projectId, Object data) {
        this.type = type;
        this.projectId = projectId;
        this.data = data;
        this.timestamp = Instant.now().toEpochMilli();
    }
}
