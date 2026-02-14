package com.codescope.backend.ai.normalization;

import com.codescope.backend.ai.model.LLMChunkResponse;
import com.codescope.backend.ai.model.LLMNormalizedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class NormalizationService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public LLMNormalizedResult normalize(LLMChunkResponse response) {
        // TODO: Implement sophisticated JSON cleaning and repair logic
        try {
            String sanitizedJson = sanitize(response.getRawResponse());
            return objectMapper.readValue(sanitizedJson, LLMNormalizedResult.class);
        } catch (Exception e) {
            // Handle parsing failure
            return new LLMNormalizedResult(); // Return empty result
        }
    }

    private String sanitize(String rawResponse) {
        // TODO: Implement robust JSON sanitization
        return rawResponse;
    }
}
