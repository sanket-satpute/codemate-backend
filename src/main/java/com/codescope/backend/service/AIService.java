package com.codescope.backend.service;

import com.codescope.backend.ai.AIServiceFactory;
import com.codescope.backend.ai.LLMService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AIService {

    private final AIServiceFactory aiServiceFactory;

    @Value("${ai.defaultModel:huggingface}")
    private String defaultProvider;

    public AIService(AIServiceFactory aiServiceFactory) {
        this.aiServiceFactory = aiServiceFactory;
    }

    public Flux<String> chatStream(String prompt) {
        log.info("Initiating chat stream with prompt: {}", prompt);
        LLMService service = (LLMService) aiServiceFactory.getService(defaultProvider);
        if (service == null) {
            log.error("No LLMService found for provider: {}", defaultProvider);
            return Flux.error(new RuntimeException("No LLMService found for provider: " + defaultProvider));
        }
        // Assuming LLMService can also handle streaming, if not, this part needs refinement.
        // For now, we'll cast to HuggingFaceService as it's the only one with stream capabilities
        // and add a fallback if defaultProvider is not HuggingFace.
        if (service instanceof com.codescope.backend.ai.HuggingFaceService hfService) {
            return hfService.queryDefaultModelStream(prompt)
                    .doOnComplete(() -> log.info("Chat stream completed for prompt: {}", prompt))
                    .doOnError(e -> log.error("Error in chat stream for prompt: {}", prompt, e));
        } else {
            log.warn("Streaming not supported for provider: {}, falling back to non-streaming query.", defaultProvider);
            return service.queryModel(prompt).flux()
                    .doOnComplete(() -> log.info("Chat stream (fallback non-streaming) completed for prompt: {}", prompt))
                    .doOnError(e -> log.error("Error in chat stream (fallback non-streaming) for prompt: {}", prompt, e));
        }
    }

    public Mono<String> generateCorrectedCode(String originalCode, String instructions) {
        log.info("Generating corrected code");
        LLMService service = (LLMService) aiServiceFactory.getService(defaultProvider);
        if (service == null) {
            log.error("No LLMService found for provider: {}", defaultProvider);
            return Mono.error(new RuntimeException("No LLMService found for provider: " + defaultProvider));
        }
        String prompt = String.format("You are a code correction AI. Correct the following code based on the instructions. Original code:\n%s\nInstructions:\n%s", originalCode, instructions);
        return service.queryModel(prompt)
                .doOnSuccess(response -> log.info("Successfully generated corrected code"))
                .doOnError(e -> log.error("Error generating corrected code", e));
    }

    public Mono<String> analyzeCode(String code, String context) {
        log.info("Analyzing code with context: {}", context);
        LLMService service = (LLMService) aiServiceFactory.getService(defaultProvider);
        if (service == null) {
            log.error("No LLMService found for provider: {}", defaultProvider);
            return Mono.error(new RuntimeException("No LLMService found for provider: " + defaultProvider));
        }
        String prompt = String.format("You are a code analysis AI. Analyze the following code and provide key points, potential bugs, and improvements. Code:\n%s\nContext:\n%s", code, context);
        return service.queryModel(prompt)
                .doOnSuccess(response -> log.info("Successfully analyzed code"))
                .doOnError(e -> log.error("Error analyzing code", e));
    }

    public boolean isLiveMode() {
        // This can be configured via application.properties if needed
        log.debug("Checking live mode status");
        return true; // Placeholder for now
    }
}
