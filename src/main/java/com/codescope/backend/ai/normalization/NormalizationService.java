package com.codescope.backend.ai.normalization;

import com.codescope.backend.ai.model.LLMChunkResponse;
import com.codescope.backend.ai.model.LLMNormalizedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class NormalizationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LLMNormalizedResult normalize(LLMChunkResponse response) {
        try {
            String sanitizedJson = sanitize(response.getRawResponse());
            return objectMapper.readValue(sanitizedJson, LLMNormalizedResult.class);
        } catch (Exception e) {
            log.error("Failed to parse LLM Response into JSON for chunk {}. Error: {}", response.getChunkId(),
                    e.getMessage());
            LLMNormalizedResult emptyResult = new LLMNormalizedResult();
            emptyResult.setChunkId(response.getChunkId());
            return emptyResult;
        }
    }

    private String sanitize(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return "{}";
        }

        // Match JSON block if enclosed in markdown
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(rawResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Fallback: finding the first '{' and last '}'
        int startIndex = rawResponse.indexOf('{');
        int endIndex = rawResponse.lastIndexOf('}');
        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            return rawResponse.substring(startIndex, endIndex + 1);
        }

        return rawResponse.trim();
    }
}
