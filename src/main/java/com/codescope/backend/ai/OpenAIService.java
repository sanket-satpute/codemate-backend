package com.codescope.backend.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * OpenAI implementation using Spring AI ChatClient.
 * Replaces the raw WebClient + manual JSON parsing approach.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "ai.chat.client.enabled", havingValue = "true", matchIfMissing = false)
public class OpenAIService implements LLMService {

    private final ChatClient chatClient;

    public OpenAIService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Mono<String> queryModel(String prompt) {
        return chat(prompt);
    }

    /**
     * Send a chat-style prompt and return a Mono<String> with the full response.
     */
    public Mono<String> chat(String prompt) {
        log.info("OpenAIService: sending chat request via Spring AI (promptLen={})",
                prompt == null ? 0 : prompt.length());

        return Mono.fromCallable(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content()).doOnSuccess(result -> log.info("OpenAIService: chat response received (len={})",
                        result == null ? 0 : result.length()))
                .onErrorResume(ex -> {
                    log.error("OpenAIService chat failed: {}", ex.getMessage());
                    return Mono.error(new RuntimeException("OpenAI API chat failed: " + ex.getMessage()));
                });
    }

    /**
     * Send a chat-style prompt and return a Flux<String> for streaming content.
     */
    public Flux<String> chatStream(String prompt) {
        log.info("OpenAIService: sending chat stream request via Spring AI (promptLen={})",
                prompt == null ? 0 : prompt.length());

        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .doOnComplete(() -> log.info("OpenAIService: stream completed"))
                .onErrorResume(ex -> {
                    log.error("OpenAIService stream failed: {}", ex.getMessage());
                    return Flux.error(new RuntimeException("OpenAI API stream failed: " + ex.getMessage()));
                });
    }
}
