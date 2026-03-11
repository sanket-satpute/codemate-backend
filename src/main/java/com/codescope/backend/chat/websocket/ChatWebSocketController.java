package com.codescope.backend.chat.websocket;

import com.codescope.backend.chat.ChatService;
import com.codescope.backend.chat.dto.ChatRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST endpoint for fire-and-forget chat messages.
 * The frontend sends chat messages via HTTP POST; real-time responses
 * are pushed back via the reactive WebSocket /topic/chat/* subscriptions.
 */
@RestController
@RequestMapping("/api/chat/ws")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatWebSocketController {

    private final ChatService chatService;

    @PostMapping("/{projectId}/sendMessage")
    public Mono<Void> sendMessage(@PathVariable String projectId, @RequestBody ChatRequest chatRequest) {
        return chatService.addUserMessage(projectId, chatRequest.getMessage())
                .flatMap(userMessage -> chatService.processUserMessage(projectId, chatRequest.getMessage()))
                .then();
    }
}
