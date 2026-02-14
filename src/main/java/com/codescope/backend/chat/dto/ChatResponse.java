package com.codescope.backend.chat.dto;

import com.codescope.backend.chat.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatResponse {
    private ChatMessage userMessage;
    private ChatMessage aiMessage;
}
