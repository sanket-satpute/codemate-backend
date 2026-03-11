package com.codescope.backend.controller;

import com.codescope.backend.chat.dto.ChatMessageDTO;
import com.codescope.backend.chat.dto.ChatRequestDTO;
import com.codescope.backend.chat.dto.ChatResponseDTO;
import com.codescope.backend.chat.ChatService;
import com.codescope.backend.dto.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/{projectId}/send")
    public Mono<ResponseEntity<BaseResponse<ChatResponseDTO>>> sendMessage(
            @PathVariable String projectId,
            @Valid @RequestBody ChatRequestDTO chatRequest) {
        return chatService.addUserMessage(projectId, chatRequest.getMessage())
                .flatMap(userMessage -> chatService.processUserMessage(projectId, chatRequest.getMessage())
                        .map(aiMessage -> new ChatResponseDTO(
                                ChatMessageDTO.fromEntity(userMessage),
                                ChatMessageDTO.fromEntity(aiMessage))))
                .map(response -> ResponseEntity.ok(
                        BaseResponse.success(response, "Message sent successfully")))
                .onErrorResume(e -> {
                    log.error("Failed to send message for project {}: {}", projectId, e.getMessage(), e);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    return Mono.just(ResponseEntity.internalServerError()
                            .body(BaseResponse.error("Failed to send message: " + errorMsg)));
                });
    }

    @GetMapping("/{projectId}/history")
    public Mono<ResponseEntity<BaseResponse<java.util.List<ChatMessageDTO>>>> getChatHistory(
            @PathVariable String projectId) {
        return chatService.getChatHistory(projectId)
                .map(ChatMessageDTO::fromEntity)
                .collectList()
                .map(history -> ResponseEntity.ok(
                        BaseResponse.success(history, "Chat history retrieved successfully")))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(BaseResponse.error("Failed to retrieve chat history: " + e.getMessage()))));
    }

    @DeleteMapping("/{projectId}/clear")
    public Mono<ResponseEntity<BaseResponse<Void>>> clearChat(@PathVariable String projectId) {
        return chatService.clearChat(projectId)
                .then(Mono.just(ResponseEntity.ok(
                        BaseResponse.<Void>success(null, "Chat history cleared successfully"))))
                .onErrorResume(e -> Mono.just(ResponseEntity.internalServerError()
                        .body(BaseResponse.<Void>error("Failed to clear chat: " + e.getMessage()))));
    }

    @PostMapping(value = "/{projectId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamMessage(
            @PathVariable String projectId,
            @Valid @RequestBody ChatRequestDTO chatRequest) {
        return chatService.streamUserMessage(projectId, chatRequest.getMessage());
    }
}
