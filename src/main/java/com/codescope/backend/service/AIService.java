package com.codescope.backend.service;

import com.codescope.backend.ai.client.ILLMClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AIService {

    private final ILLMClient llmClient;

    public AIService(ILLMClient llmClient) {
        this.llmClient = llmClient;
    }

    public Flux<String> chatStream(String prompt) {
        log.info("Initiating chat stream with prompt: {}", prompt);
        return llmClient.chatStream(prompt)
                .doOnComplete(() -> log.info("Chat stream completed for prompt: {}", prompt))
                .doOnError(e -> log.error("Error in chat stream for prompt: {}", prompt, e));
    }

    public Mono<String> generateCorrectedCode(String originalCode, String instructions) {
        log.info("Generating corrected code");
        String prompt = String.format(
                "You are a code correction AI. Correct the following code based on the instructions. Original code:\n%s\nInstructions:\n%s",
                originalCode, instructions);
        return llmClient.chat(prompt)
                .doOnSuccess(response -> log.info("Successfully generated corrected code"))
                .doOnError(e -> log.error("Error generating corrected code", e));
    }

    public Mono<String> analyzeCode(String code, String context) {
        log.info("Analyzing code with context: {}", context);
        String prompt = String.format(
                "You are a code analysis AI. Analyze the following code and provide key points, potential bugs, and improvements. Code:\n%s\nContext:\n%s",
                code, context);
        return llmClient.chat(prompt)
                .doOnSuccess(response -> log.info("Successfully analyzed code"))
                .doOnError(e -> log.error("Error analyzing code", e));
    }

    public boolean isLiveMode() {
        // This can be configured via application.properties if needed
        log.debug("Checking live mode status");
        return true; // Placeholder for now
    }
}
