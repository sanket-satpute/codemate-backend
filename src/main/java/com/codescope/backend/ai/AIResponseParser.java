package com.codescope.backend.ai;

import com.codescope.backend.ai.exception.ResponseParsingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AIResponseParser {

    private final ObjectMapper objectMapper;

    public String parseAndValidateJson(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            throw new ResponseParsingException("AI response is empty or null.");
        }

        // Attempt to extract JSON from markdown code fences
        String jsonString = extractJsonFromMarkdown(rawResponse);

        try {
            JsonNode rootNode = objectMapper.readTree(jsonString);

            // Basic validation for expected fields
            boolean hasSummary = rootNode.has("summary") && rootNode.get("summary").isTextual();
            boolean hasIssues = rootNode.has("issues") && rootNode.get("issues").isArray();
            boolean hasSuggestions = rootNode.has("suggestions") && rootNode.get("suggestions").isArray();
            boolean hasRiskLevel = rootNode.has("riskLevel") && rootNode.get("riskLevel").isTextual();

            if (!hasSummary || !hasIssues || !hasSuggestions || !hasRiskLevel) {
                log.warn("Parsed JSON is missing strict required fields. Attempting fallback generation.");
                return generateFallbackJson(rawResponse);
            }

            // Further validation for riskLevel enum values (optional but good practice)
            String riskLevel = rootNode.get("riskLevel").asText();
            if (!List.of("LOW", "MEDIUM", "HIGH").contains(riskLevel.toUpperCase())) {
                log.warn("AI response contained an unexpected riskLevel: {}", riskLevel);
            }

            return objectMapper.writeValueAsString(rootNode); // Return pretty-printed JSON
        } catch (Exception e) {
            log.error("Failed to parse AI response JSON: {}. Executing Fallback JSON strategy.", e.getMessage());
            return generateFallbackJson(rawResponse);
        }
    }

    private String generateFallbackJson(String rawResponse) {
        try {
            var fallbackNode = objectMapper.createObjectNode();
            // Store raw context safely in summary
            String safeSummary = "The AI returned an unstructured response. Raw Output:\n\n" + rawResponse;
            if (safeSummary.length() > 2000) {
                safeSummary = safeSummary.substring(0, 2000) + "... [truncated]"; // Avoid massive UI blobs
            }

            fallbackNode.put("summary", safeSummary);
            fallbackNode.putArray("issues");

            var suggestionNode = objectMapper.createObjectNode();
            suggestionNode.put("description", "Review the summary for the full unstructured AI response.");
            suggestionNode.put("file", "Multiple");

            fallbackNode.putArray("suggestions").add(suggestionNode);
            fallbackNode.put("riskLevel", "MEDIUM");

            return objectMapper.writeValueAsString(fallbackNode);
        } catch (Exception ex) {
            throw new ResponseParsingException("Critical failure generating fallback JSON: " + ex.getMessage(), ex);
        }
    }

    private String extractJsonFromMarkdown(String text) {
        // Regex to find content within ```json ... ``` or ``` ... ```
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // If no markdown code fence, assume the whole text is JSON
        return text;
    }
}
