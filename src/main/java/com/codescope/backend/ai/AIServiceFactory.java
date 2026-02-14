package com.codescope.backend.ai;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AIServiceFactory {

    @Autowired private HuggingFaceService huggingFaceService;
    @Autowired private OpenAIService openAIService;
    @Autowired private OllamaService ollamaService;

    public Object getService(String provider) {
        return switch (provider.toLowerCase()) {
            case "huggingface" -> huggingFaceService;
            case "openai" -> openAIService;
            case "ollama" -> ollamaService;
            default -> null;
        };
    }
}
