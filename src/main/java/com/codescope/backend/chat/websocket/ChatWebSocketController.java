package com.codescope.backend.chat.websocket;

import com.codescope.backend.chat.ChatMessage;
import com.codescope.backend.chat.ChatService;
import com.codescope.backend.chat.dto.ChatRequest;
import com.codescope.backend.realtime.websocket.WebSocketEventPayload;
import com.codescope.backend.realtime.websocket.WebSocketEventPublisher;
import com.codescope.backend.realtime.websocket.WebSocketEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final WebSocketEventPublisher webSocketEventPublisher;

    @MessageMapping("/chat/{projectId}/sendMessage")
    public void sendMessage(@DestinationVariable String projectId, @Payload ChatRequest chatRequest) {
        chatService.addUserMessage(projectId, chatRequest.getMessage())
                .subscribe(userMessage -> {
                    webSocketEventPublisher.publishToProject(projectId,
                            new WebSocketEventPayload(WebSocketEventType.CHAT_USER_MESSAGE, userMessage.getId().toString(), Map.of("message", userMessage, "messageId", userMessage.getId().toString())));

                    // Trigger AI processing and send response
                    chatService.processUserMessage(projectId, chatRequest.getMessage())
                            .subscribe(aiMessage -> webSocketEventPublisher.publishToProject(projectId,
                                    new WebSocketEventPayload(WebSocketEventType.CHAT_AI_MESSAGE, aiMessage.getId().toString(), Map.of("message", aiMessage, "messageId", aiMessage.getId().toString()))));
                });
    }
}
