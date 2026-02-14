package com.codescope.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import reactor.core.publisher.Flux; // Import Flux for reactive streaming
import java.time.Duration;
import java.util.Objects;

/**
 * ✅ Fully functional Hugging Face Service
 * Queries free inference API and returns clean AI output text.
 * Refactored to use reactive non-blocking calls.
 */
@Service
public class HuggingFaceService implements LLMService {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceService.class);

    @Value("${ai.huggingface.api.key}")
    private String apiKey;

    @Value("${ai.huggingface.default-model:google/flan-t5-base}")
    private String defaultModel;

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Query a Hugging Face text generation model for a given code prompt.
     * Returns a Mono<String> for reactive processing.
     */
    private Mono<String> queryModel(String modelId, String prompt) {
        String url = "https://api.inference.huggingface.co/models/" + modelId; // Corrected URL for inference API

        String payload = """
        {
          "inputs": "You are a code analysis AI. Analyze the following code and provide key points, potential bugs, and improvements:\\n%s",
          "parameters": {
            "max_new_tokens": 256,
            "temperature": 0.5,
            "top_p": 0.9
          }
        }
        """.formatted(prompt.replace("\"", "\\\""));

        log.info("📡 HuggingFace API: model={}, promptLen={}", modelId, prompt.length());

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(45)) // Use timeout operator instead of block
                .flatMap(raw -> { // Use flatMap to process the result reactively
                    if (raw == null || raw.trim().isEmpty()) {
                        log.warn("⚠️ Empty HuggingFace response for model={}", modelId);
                        return Mono.empty(); // Return empty Mono for no response
                    }

                    try {
                        JsonNode root = mapper.readTree(raw);
                        if (root.isArray() && root.size() > 0) {
                            JsonNode first = root.get(0);
                            if (first.has("generated_text")) {
                                return Mono.just(first.get("generated_text").asText());
                            }
                        }
                        if (root.has("generated_text")) {
                            return Mono.just(root.get("generated_text").asText());
                        }
                        if (root.has("error")) {
                            log.error("❌ HF API error: {}", root.get("error").asText());
                            return Mono.empty(); // Return empty Mono on error
                        }
                        log.error("HuggingFace called with unexpected response format for model={}: {}", modelId, raw);
                        return Mono.just(raw); // Fallback to raw text if format is unexpected but not an error
                    } catch (Exception e) {
                        log.error("❌ HuggingFace response parsing failed for model={}: {}", modelId, e.getMessage());
                        return Mono.empty(); // Return empty Mono on parsing error
                    }
                })
                .onErrorResume(e -> { // Handle network errors or timeouts
                    log.error("❌ HuggingFace API call failed for model={}: {}", modelId, e.getMessage());
                    return Mono.empty(); // Return empty Mono on error
                });
    }

    @Override
    public Mono<String> queryModel(String input) {
        return queryDefaultModel(input);
    }

    /**
     * Try multiple models in fallback order.
     * Returns a Mono<String> representing the result.
     */
    public Mono<String> queryDefaultModel(String prompt) {
        String[] models = {
                defaultModel, // Use the configurable default model first
                "tiiuae/falcon-7b-instruct" // Fallback model
        };

        // Use Flux.fromArray and flatMap sequentially to try models
        return Flux.fromArray(models)
                .flatMap(model -> queryModel(model, prompt)
                        .map(result -> {
                            if (result != null && !result.toLowerCase().contains("error")) {
                                log.info("✅ Model {} succeeded", model);
                                return result;
                            }
                            return null; // Indicate failure for this model
                        })
                        .onErrorResume(e -> { // If queryModel itself fails, treat as null
                            log.warn("Error querying model {}: {}", model, e.getMessage());
                            return Mono.justOrEmpty(null);
                        })
                )
                .filter(Objects::nonNull) // Filter out null results (failures)
                .next() // Take the first non-null result
                .switchIfEmpty(Mono.just("⚠️ No model returned valid response.")); // Default if all fail
    }

    /**
     * Query a Hugging Face text generation model for a given code prompt, streaming the response.
     * Returns a Flux<String> for reactive streaming processing.
     */
    public Flux<String> queryModelStream(String modelId, String prompt) {
        String url = "https://api.inference.huggingface.co/models/" + modelId;

        String payload = """
        {
          "inputs": "You are a code analysis AI. Analyze the following code and provide key points, potential bugs, and improvements:\\n%s",
          "parameters": {
            "max_new_tokens": 500,
            "temperature": 0.5,
            "top_p": 0.9,
            "return_full_text": false,
            "stream": true
          }
        }
        """.formatted(prompt.replace("\"", "\\\""));

        log.info("📡 HuggingFace API Stream: model={}, promptLen={}", modelId, prompt.length());

        return webClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(String.class) // Get raw chunks
                .flatMap(rawChunk -> {
                    try {
                        // Hugging Face streaming often sends multiple JSON objects in one chunk,
                        // or non-JSON data. We need to parse carefully.
                        // This is a simplified parser. Real-world might need more robust JSON stream parsing.
                        if (rawChunk.trim().isEmpty()) {
                            return Flux.empty();
                        }
                        
                        // Attempt to parse as JSON array or single object
                        JsonNode root = mapper.readTree(rawChunk);
                        if (root.isArray()) {
                            return Flux.fromIterable(root)
                                    .filter(node -> node.has("token") && node.path("token").has("text"))
                                    .map(node -> node.path("token").path("text").asText())
                                    .filter(text -> !text.equals("<s>") && !text.equals("</s>") && !text.equals("<unk>")); // Filter special tokens
                        } else if (root.has("token") && root.path("token").has("text")) {
                            String text = root.path("token").path("text").asText();
                            if (!text.equals("<s>") && !text.equals("</s>") && !text.equals("<unk>")) {
                                return Flux.just(text);
                            }
                        } else if (root.has("generated_text")) { // For non-streaming fallback or final chunk
                            return Flux.just(root.get("generated_text").asText());
                        } else if (root.has("error")) {
                            log.error("❌ HF API stream error: {}", root.get("error").asText());
                            return Flux.error(new RuntimeException("HuggingFace API error: " + root.get("error").asText()));
                        }
                        // If it's not a recognized JSON format, treat as raw text chunk
                        return Flux.just(rawChunk);

                    } catch (Exception e) {
                        log.warn("⚠️ HuggingFace stream chunk parsing failed: {}. Raw chunk: {}", e.getMessage(), rawChunk);
                        // Fallback to returning the raw chunk if parsing fails
                        return Flux.just(rawChunk);
                    }
                })
                .timeout(Duration.ofSeconds(90)) // Longer timeout for streaming
                .onErrorResume(e -> {
                    log.error("❌ HuggingFace API stream call failed for model={}: {}", modelId, e.getMessage());
                    return Flux.error(new RuntimeException("HuggingFace API stream failed: " + e.getMessage()));
                });
    }

    /**
     * Try multiple models in fallback order for streaming.
     * Returns a Flux<String> representing the streamed result.
     */
    public Flux<String> queryDefaultModelStream(String prompt) {
        String[] models = {
                defaultModel, // Use the configurable default model first
                "tiiuae/falcon-7b-instruct" // Fallback model
        };

        // Use Flux.fromArray and flatMap sequentially to try models
        return Flux.fromArray(models)
                .flatMap(model -> queryModelStream(model, prompt)
                        .doOnSubscribe(s -> log.info("Attempting to stream from model: {}", model))
                        .doOnError(e -> log.warn("Error streaming from model {}: {}", model, e.getMessage()))
                        .onErrorResume(e -> Flux.empty()) // On error, switch to empty flux to try next model
                )
                .take(1) // Take the first successful stream
                .switchIfEmpty(Flux.just("⚠️ No model returned valid streaming response.")) // Default if all fail
                .doOnError(e -> log.error("❌ All HuggingFace models failed to stream: {}", e.getMessage()));
    }
}
