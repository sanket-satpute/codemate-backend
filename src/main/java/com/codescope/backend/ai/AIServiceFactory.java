package com.codescope.backend.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Factory to select the appropriate LLM service based on configured provider.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.legacy.factory.enabled", havingValue = "true", matchIfMissing = false)
public class AIServiceFactory {

    private final HuggingFaceService huggingFaceService;
    private final OpenAIService openAIService;
    private final OllamaService ollamaService;

    /**
     * Returns the correct LLMService based on the configured AI provider name.
     */
    public LLMService getService(String provider) {
        return switch (provider.toLowerCase()) {
            case "huggingface" -> huggingFaceService;
            case "openai" -> openAIService;
            case "ollama" -> ollamaService;
            default -> throw new IllegalArgumentException("Unsupported AI provider: " + provider);
        };
    }
}
