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
import reactor.core.publisher.Mono; // Import Mono for reactive operations

import java.time.Duration;
import java.util.Objects;

@Service
public class OllamaService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);

    @Value("${ai.ollama.host:http://localhost}")
    private String host;

    @Value("${ai.ollama.port:11434}")
    private int port;

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Send prompt to Ollama server (local model)
     * Returns a Mono<String> for reactive processing.
     */
    private Mono<String> query(String model, String prompt) {
        String url = host + ":" + port + "/api/generate";
        String requestBody = String.format("""
        {
            "model": "%s",
            "prompt": "%s",
            "stream": false
        }
        """, model, prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        log.info("📡 Sending request to Ollama: model={}, promptLen={}", model, prompt.length());

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(60)) // Added timeout for Ollama calls
                .flatMap(response -> {
                    if (response == null) {
                        log.warn("Ollama returned null response for model={}", model);
                        return Mono.empty();
                    }
                    // Check for common error patterns in Ollama response
                    try {
                        JsonNode root = mapper.readTree(response);
                        if (root.has("error")) {
                            log.error("Ollama API error for model {}: {}", model, root.get("error").asText());
                            return Mono.empty();
                        }
                        // Extract the generated text, assuming it's in "response" field
                        if (root.has("response")) {
                            return Mono.just(root.get("response").asText());
                        } else {
                            log.warn("Ollama response missing 'response' field for model {}. Raw: {}", model, response);
                            return Mono.empty();
                        }
                    } catch (Exception e) {
                        log.error("Ollama response parsing failed for model {}. Error: {}", model, e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ Ollama call failed for model {}: {}", model, e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<String> queryModel(String input) {
        return queryDefault(input);
    }

    /**
     * Try common models sequentially.
     * Returns a Mono<String> representing the result.
     */
    public Mono<String> queryDefault(String prompt) {
        String[] models = {
                "codellama",           // lightweight
                "llama3",              // more advanced
                "mistral"              // fallback
        };

        // Use Flux.fromArray and flatMap sequentially to try models
        return Mono.just(models)
                .flatMapMany(mArr -> reactor.core.publisher.Flux.fromArray(mArr))
                .flatMap(model -> query(model, prompt)
                        .map(result -> {
                            if (result != null && !result.toLowerCase().contains("error")) {
                                log.info("✅ Ollama model {} succeeded", model);
                                return result;
                            }
                            return null; // Indicate failure for this model
                        })
                        .onErrorResume(e -> { // If query itself fails, treat as null
                            log.warn("Error querying Ollama model {}: {}", model, e.getMessage());
                            return Mono.justOrEmpty(null);
                        })
                )
                .filter(Objects::nonNull) // Filter out null results (failures)
                .next() // Take the first non-null result
                .switchIfEmpty(Mono.just("⚠️ No Ollama model responded successfully.")); // Default if all fail
    }

    /**
     * Send prompt to Ollama server (local model) for streaming response.
     * Returns a Flux<String> for reactive streaming processing.
     */
    public Flux<String> queryStream(String model, String prompt) {
        String url = host + ":" + port + "/api/generate";
        String requestBody = String.format("""
        {
            "model": "%s",
            "prompt": "%s",
            "stream": true
        }
        """, model, prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        log.info("📡 Sending streaming request to Ollama: model={}, promptLen={}", model, prompt.length());

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class) // Get raw chunks
                .flatMap(rawChunk -> {
                    if (rawChunk == null || rawChunk.trim().isEmpty()) {
                        return Flux.empty();
                    }
                    try {
                        JsonNode root = mapper.readTree(rawChunk);
                        if (root.has("error")) {
                            log.error("Ollama API stream error for model {}: {}", model, root.get("error").asText());
                            return Flux.error(new RuntimeException("Ollama API error: " + root.get("error").asText()));
                        }
                        if (root.has("response")) {
                            return Flux.just(root.get("response").asText());
                        } else {
                            // If it's not a recognized JSON format or missing 'response', treat as raw text chunk
                            return Flux.just(rawChunk);
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ Ollama stream chunk parsing failed: {}. Raw chunk: {}", e.getMessage(), rawChunk);
                        // Fallback to returning the raw chunk if parsing fails
                        return Flux.just(rawChunk);
                    }
                })
                .timeout(Duration.ofSeconds(120)) // Longer timeout for streaming
                .onErrorResume(e -> {
                    log.error("❌ Ollama API stream call failed for model {}: {}", model, e.getMessage());
                    return Flux.error(new RuntimeException("Ollama API stream failed: " + e.getMessage()));
                });
    }

    /**
     * Try common models sequentially for streaming.
     * Returns a Flux<String> representing the streamed result.
     */
    public Flux<String> queryDefaultStream(String prompt) {
        String[] models = {
                "codellama",           // lightweight
                "llama3",              // more advanced
                "mistral"              // fallback
        };

        return Flux.fromArray(models)
                .flatMap(model -> queryStream(model, prompt)
                        .doOnSubscribe(s -> log.info("Attempting to stream from Ollama model: {}", model))
                        .doOnError(e -> log.warn("Error streaming from Ollama model {}: {}", model, e.getMessage()))
                        .onErrorResume(e -> Flux.empty()) // On error, switch to empty flux to try next model
                )
                .take(1) // Take the first successful stream
                .switchIfEmpty(Flux.just("⚠️ No Ollama model responded successfully for streaming.")) // Default if all fail
                .doOnError(e -> log.error("❌ All Ollama models failed to stream: {}", e.getMessage()));
    }
}
