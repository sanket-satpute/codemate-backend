package com.codescope.backend.ai;

import com.codescope.backend.ai.exception.AIServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OpenAIClient implements LLMClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String defaultModel;

    public OpenAIClient(@Value("${ai.openai.api.url}") String apiUrl,
                        @Value("${ai.openai.api.key}") String apiKey,
                        @Value("${ai.openai.default.model:gpt-4o-mini}") String defaultModel,
                        WebClient.Builder webClientBuilder,
                        ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.defaultModel = defaultModel;
    }

    @Override
    public String sendPrompt(String prompt) {
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", prompt
        );
        Map<String, Object> requestBody = Map.of(
                "model", defaultModel,
                "messages", List.of(message),
                "response_format", Map.of("type", "text")
        );

        return webClient.post()
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        return objectMapper.readTree(response)
                                .get("choices").get(0)
                                .get("message").get("content").asText();
                    } catch (Exception e) {
                        log.error("Error parsing OpenAI response: {}", e.getMessage());
                        throw new AIServiceException("Failed to parse OpenAI response", e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("OpenAI API error: {}", e.getMessage());
                    return reactor.core.publisher.Mono.error(new AIServiceException("OpenAI API call failed", e));
                })
                .block(); // Blocking for simplicity, consider reactive approach for production
    }

    @Override
    public String sendJSONPrompt(Map<String, Object> data) {
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", data // OpenAI can often handle JSON objects directly in content
        );
        Map<String, Object> requestBody = Map.of(
                "model", defaultModel,
                "messages", List.of(message),
                "response_format", Map.of("type", "json_object") // Request JSON object response
        );

        return webClient.post()
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        return objectMapper.readTree(response)
                                .get("choices").get(0)
                                .get("message").get("content").asText();
                    } catch (Exception e) {
                        log.error("Error parsing OpenAI JSON response: {}", e.getMessage());
                        throw new AIServiceException("Failed to parse OpenAI JSON response", e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("OpenAI API error for JSON prompt: {}", e.getMessage());
                    return reactor.core.publisher.Mono.error(new AIServiceException("OpenAI API call failed for JSON prompt", e));
                })
                .block(); // Blocking for simplicity, consider reactive approach for production
    }
}
