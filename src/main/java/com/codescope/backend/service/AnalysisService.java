package com.codescope.backend.service;

import com.codescope.backend.ai.AIServiceFactory;
import com.codescope.backend.ai.LLMService;
import com.codescope.backend.dto.analysis.AIResponseDto;
import com.codescope.backend.model.AIResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AnalysisService {

    private final AIServiceFactory aiServiceFactory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.defaultModel:huggingface}")
    private String defaultProvider;

    @Value("${ai.huggingface.token:}")
    private String hfToken;

    public AnalysisService(AIServiceFactory aiServiceFactory) {
        this.aiServiceFactory = aiServiceFactory;
    }

    /**
     * Analyze input text/code and return a structured AIResponseDto.
     *
     * @param input The text or code to be analyzed.
     * @return A Mono emitting the AIResponseDto.
     */
    public Mono<AIResponseDto> analyzeAndBuildResponse(String input) {
        String provider = defaultProvider.toLowerCase();
        Object serviceObj = aiServiceFactory.getService(provider);

        if (!(serviceObj instanceof LLMService)) {
            log.warn("Unknown or non-LLM provider '{}', falling back to HuggingFace/Ollama", provider);
            provider = hfToken.isEmpty() ? "ollama" : "huggingface";
            serviceObj = aiServiceFactory.getService(provider);
        }

        if (!(serviceObj instanceof LLMService service)) {
            log.warn("No active AI service found, using fallback mock response.");
            return Mono.just(buildFallback("Mock", "No AI provider configured."));
        }
        final String finalProvider = provider;

        return service.queryModel(input).flatMap(raw -> {
            if (raw == null || raw.isBlank()) {
                return Mono.just(buildFallback(finalProvider, "AI returned no response."));
            }

            try {
                String summary = extractSummary(raw);
                List<Map<String, Object>> findings = extractFindings(raw);
                AIResponse resp = new AIResponse();
                resp.setProvider(finalProvider);
                resp.setSummary(summary);
                resp.setFindings(findings);
                resp.setStatus("COMPLETED");
                resp.setMetadata(Map.of(
                        "timestamp", Instant.now().toString(),
                        "provider", finalProvider,
                        "sourceRawLen", raw.length()
                ));
                resp.setGeneratedAt(new Date());

                log.info("✅ AnalysisService: completed with provider={} (summaryLen={})", finalProvider, summary.length());
                return Mono.just(convertToDto(resp));
            } catch (Exception e) {
                log.error("❌ AnalysisService failed during response processing: {}", e.getMessage());
                return Mono.just(buildFallback(finalProvider, "Exception during processing: " + e.getMessage()));
            }
        });
    }

    /**
     * Extracts detailed findings from the raw AI response.
     *
     * @param raw The raw JSON or text response from the AI.
     * @return A list of maps representing the findings.
     */
    public List<Map<String, Object>> extractFindings(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = mapper.readTree(raw);
            if (root.has("findings") && root.get("findings").isArray()) {
                return mapper.convertValue(root.get("findings"), new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception e) {
            log.error("Error parsing findings from AI response", e);
        }

        return List.of(Map.of("detail", "Could not parse detailed findings."));
    }

    public String extractSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        try {
            JsonNode root = mapper.readTree(raw);

            // Common patterns for AI responses
            // 1. Direct text field
            String[] summaryFields = {"generated_text", "text", "content", "response", "output"};
            for (String field : summaryFields) {
                if (root.has(field)) return root.get(field).asText();
            }

            // 2. Array of results
            if (root.isArray() && root.size() > 0) {
                JsonNode firstElement = root.get(0);
                for (String field : summaryFields) {
                    if (firstElement.has(field)) return firstElement.get(field).asText();
                }
                // If the first element is an object with a 'message' field containing 'content'
                if (firstElement.has("message") && firstElement.get("message").has("content")) {
                    return firstElement.get("message").get("content").asText();
                }
            }

            // 3. Nested structures like "choices" array
            if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                JsonNode firstChoice = root.get("choices").get(0);
                if (firstChoice.has("text")) return firstChoice.get("text").asText();
                if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                    return firstChoice.get("message").get("content").asText();
                }
            }

            // If no specific field is found, return the raw content if it's a simple string node
            if (root.isTextual()) {
                return root.asText();
            }

            // Fallback: return a truncated version of the raw response if parsing fails or no specific field is found
            log.warn("Could not find specific summary field in AI response. Returning raw content.");
            return raw.length() > 10000 ? raw.substring(0, 10000) : raw;

        } catch (Exception e) {
            // If JSON parsing itself fails, it might be plain text or malformed.
            // Return a truncated version of the raw response.
            log.error("Error parsing AI response as JSON, returning raw content: {}", e.getMessage());
            return raw.length() > 10000 ? raw.substring(0, 10000) : raw;
        }
    }

    private AIResponseDto buildFallback(String provider, String message) {
        AIResponse fallback = new AIResponse();
        fallback.setProvider(provider);
        fallback.setSummary(message);
        fallback.setFindings(List.of(Map.of("type", "error", "detail", "fallback used")));
        fallback.setStatus("FAILED");
        fallback.setMetadata(Map.of("timestamp", Instant.now().toString()));
        fallback.setGeneratedAt(new Date());
        return convertToDto(fallback);
    }

    private AIResponseDto convertToDto(AIResponse aiResponse) {
        return new AIResponseDto(aiResponse);
    }
}
