package com.codescope.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux; // Import Flux for reactive streaming
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Lightweight OpenAI client for chat completions.
 * Refactored to use reactive non-blocking calls.
 * Improved error handling for parsing.
 */
@Service
public class OpenAIService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    @Value("${ai.openai.token:}")
    private String openAIToken;

    private final WebClient webClient = WebClient.builder().baseUrl("https://api.openai.com").build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Send a chat-style prompt and return a Flux<String> for streaming content.
     */
    public Flux<String> chatStream(String prompt) {
        if (openAIToken == null || openAIToken.isBlank()) {
            log.warn("OpenAI token not configured.");
            return Flux.error(new RuntimeException("OpenAI token not configured."));
        }

        String body = """
        {
          "model":"gpt-4o-mini",
          "messages":[{"role":"user","content":"%s"}],
          "temperature":0.2,
          "stream": true
        }
        """.formatted(escape(prompt));

        log.info("OpenAIService: sending chat stream request (promptLen={})", prompt == null ? 0 : prompt.length());

        return webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + openAIToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class) // Get raw SSE chunks
                .flatMap(rawChunk -> {
                    if (rawChunk.trim().isEmpty() || "[DONE]".equals(rawChunk.trim())) {
                        return Flux.empty(); // Ignore empty chunks and [DONE] signal
                    }
                    try {
                        // OpenAI sends "data: {json}" for each chunk
                        String jsonString = rawChunk.replaceFirst("data: ", "");
                        JsonNode root = mapper.readTree(jsonString);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).path("delta");
                            if (delta.has("content")) {
                                return Flux.just(delta.get("content").asText());
                            }
                        }
                        return Flux.empty(); // No content in this chunk
                    } catch (Exception e) {
                        log.warn("⚠️ OpenAI stream chunk parsing failed: {}. Raw chunk: {}", e.getMessage(), rawChunk);
                        return Flux.empty(); // Skip problematic chunks
                    }
                })
                .onErrorResume(ex -> {
                    log.error("OpenAIService stream failed: {}", ex.getMessage());
                    if (log.isDebugEnabled()) log.debug("Stack:", ex);
                    return Flux.error(new RuntimeException("OpenAI API stream failed: " + ex.getMessage()));
                });
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Override
    public Mono<String> queryModel(String input) {
        return chat(input);
    }

    public Mono<String> chat(String prompt) {
        if (openAIToken == null || openAIToken.isBlank()) {
            log.warn("OpenAI token not configured.");
            return Mono.error(new RuntimeException("OpenAI token not configured."));
        }

        String body = """
        {
          "model":"gpt-4o-mini",
          "messages":[{"role":"user","content":"%s"}],
          "temperature":0.2,
          "stream": false
        }
        """.formatted(escape(prompt));

        log.info("OpenAIService: sending chat request (promptLen={})", prompt == null ? 0 : prompt.length());

        return webClient.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + openAIToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(raw -> {
                    try {
                        JsonNode root = mapper.readTree(raw);
                        JsonNode choices = root.path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode message = choices.get(0).path("message");
                            if (message.has("content")) {
                                return Mono.just(message.get("content").asText());
                            }
                        }
                        return Mono.empty();
                    } catch (Exception e) {
                        log.warn("⚠️ OpenAI response parsing failed: {}. Raw response: {}", e.getMessage(), raw);
                        return Mono.empty();
                    }
                })
                .onErrorResume(ex -> {
                    log.error("OpenAIService chat failed: {}", ex.getMessage());
                    if (log.isDebugEnabled()) log.debug("Stack:", ex);
                    return Mono.error(new RuntimeException("OpenAI API chat failed: " + ex.getMessage()));
                });
    }
}
