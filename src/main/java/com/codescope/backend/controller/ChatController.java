package com.codescope.backend.controller;

import com.codescope.backend.chat.dto.ChatMessageDTO;
import com.codescope.backend.chat.dto.ChatRequestDTO;
import com.codescope.backend.chat.dto.ChatResponseDTO;
import com.codescope.backend.chat.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/{projectId}/send")
    public ResponseEntity<?> sendMessage(@PathVariable String projectId, @Valid @RequestBody ChatRequestDTO chatRequest) {
        ChatResponseDTO response = chatService.addUserMessage(projectId, chatRequest.getMessage())
                .zipWith(chatService.processUserMessage(projectId, chatRequest.getMessage()))
                .map(tuple -> new ChatResponseDTO(
                        ChatMessageDTO.fromEntity(tuple.getT1()),
                        ChatMessageDTO.fromEntity(tuple.getT2())
                )).block();
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", "success");
        apiResponse.put("data", response);
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{projectId}/history")
    public ResponseEntity<?> getChatHistory(@PathVariable String projectId) {
        List<ChatMessageDTO> history = chatService.getChatHistory(projectId)
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList()).block();
        Map<String, Object> data = new HashMap<>();
        data.put("messages", history);
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", "success");
        apiResponse.put("data", data);
        return ResponseEntity.ok(apiResponse);
    }

    @DeleteMapping("/{projectId}/clear")
    public ResponseEntity<?> clearChat(@PathVariable String projectId) {
        chatService.clearChat(projectId).block();
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("status", "success");
        apiResponse.put("message", "Chat history cleared successfully.");
        return ResponseEntity.ok(apiResponse);
    }
}
