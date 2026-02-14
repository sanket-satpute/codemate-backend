package com.codescope.backend.chat.dto;

import com.codescope.backend.chat.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private String id;
    private String projectId;
    private ChatMessage.Sender sender;
    private String message;
    private LocalDateTime timestamp;
    private String metadata;

    public static ChatMessageDTO fromEntity(ChatMessage chatMessage) {
        return new ChatMessageDTO(
                chatMessage.getId(),
                chatMessage.getProjectId(),
                chatMessage.getSender(),
                chatMessage.getMessage(),
                chatMessage.getTimestamp(),
                chatMessage.getMetadata()
        );
    }
}
