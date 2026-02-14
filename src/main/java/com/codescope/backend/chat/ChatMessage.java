package com.codescope.backend.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
public class ChatMessage {

    @Id
    private String id; // MongoDB ID

    @NotNull(message = "Project ID cannot be null")
    @Field("projectId") // Map to projectId field in MongoDB
    private String projectId;

    @NotNull(message = "Sender cannot be null")
    private Sender sender;

    @NotBlank(message = "Message cannot be blank")
    private String message;

    @CreatedDate
    private LocalDateTime timestamp;

    private String metadata;

    public enum Sender {
        USER, AI
    }
}
