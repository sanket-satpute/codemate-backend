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
            if (!rootNode.has("summary") || !rootNode.get("summary").isTextual()) {
                throw new ResponseParsingException("Parsed JSON is missing 'summary' field or it's not a string.");
            }
            if (!rootNode.has("issues") || !rootNode.get("issues").isArray()) {
                throw new ResponseParsingException("Parsed JSON is missing 'issues' field or it's not an array.");
            }
            if (!rootNode.has("suggestions") || !rootNode.get("suggestions").isArray()) {
                throw new ResponseParsingException("Parsed JSON is missing 'suggestions' field or it's not an array.");
            }
            if (!rootNode.has("riskLevel") || !rootNode.get("riskLevel").isTextual()) {
                throw new ResponseParsingException("Parsed JSON is missing 'riskLevel' field or it's not a string.");
            }

            // Further validation for riskLevel enum values (optional but good practice)
            String riskLevel = rootNode.get("riskLevel").asText();
            if (!List.of("LOW", "MEDIUM", "HIGH").contains(riskLevel.toUpperCase())) {
                log.warn("AI response contained an unexpected riskLevel: {}", riskLevel);
                // Optionally, throw an exception or normalize to a default value
            }

            return objectMapper.writeValueAsString(rootNode); // Return pretty-printed JSON
        } catch (Exception e) {
            log.error("Failed to parse or validate AI response JSON: {}", e.getMessage());
            throw new ResponseParsingException("Failed to parse or validate AI response JSON: " + e.getMessage(), e);
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
