package com.codescope.backend.dto.chat;

import com.codescope.backend.chat.ChatMessage; // Import ChatMessage for Sender enum
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; // Use LocalDateTime instead of Instant

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private String id; // Keep as String for Firebase document ID
    private Long projectId; // Changed to Long to match ChatMessage model
    private ChatMessage.Sender sender; // Changed to enum to match ChatMessage model
    private String message;
    private LocalDateTime timestamp; // Changed to LocalDateTime to match ChatMessage model
}
