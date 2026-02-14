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

import java.util.Collections;
import java.util.Map;

@Component
@Slf4j
public class HuggingFaceClient implements LLMClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public HuggingFaceClient(@Value("${ai.huggingface.api.url}") String apiUrl,
                             @Value("${ai.huggingface.api.key}") String apiKey,
                             WebClient.Builder webClientBuilder,
                             ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String sendPrompt(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "inputs", prompt,
                "parameters", Map.of(
                        "max_new_tokens", 1000,
                        "temperature", 0.7
                ),
                "options", Map.of(
                        "wait_for_model", true
                )
        );

        return webClient.post()
                .body(BodyInserters.fromValue(requestBody))
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        // HuggingFace API often returns a JSON array with a single object
                        // e.g., [{"generated_text": "..."}]
                        // We need to extract the generated_text
                        return objectMapper.readTree(response).get(0).get("generated_text").asText();
                    } catch (Exception e) {
                        log.error("Error parsing HuggingFace response: {}", e.getMessage());
                        throw new AIServiceException("Failed to parse HuggingFace response", e);
                    }
                })
                .onErrorResume(e -> {
                    log.error("HuggingFace API error: {}", e.getMessage());
                    return reactor.core.publisher.Mono.error(new AIServiceException("HuggingFace API call failed", e));
                })
                .block(); // Blocking for simplicity, consider reactive approach for production
    }

    @Override
    public String sendJSONPrompt(Map<String, Object> data) {
        // HuggingFace often expects a simple string prompt, even for JSON-like inputs.
        // We'll convert the map to a JSON string and send it as the prompt.
        try {
            String jsonString = objectMapper.writeValueAsString(data);
            return sendPrompt(jsonString);
        } catch (Exception e) {
            log.error("Error converting map to JSON string for HuggingFace: {}", e.getMessage());
            throw new AIServiceException("Failed to convert JSON map to string for HuggingFace prompt", e);
        }
    }
}
