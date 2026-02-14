package com.codescope.backend.ai.client;

import com.codescope.backend.ai.model.FileChunk;
import com.codescope.backend.ai.model.LLMChunkResponse;
import org.springframework.stereotype.Service;

@Service
public class GeminiClient implements ILLMClient {

    @Override
    public LLMChunkResponse performAnalysis(FileChunk chunk) {
        // TODO: Implement Gemini API call
        return null;
    }
}
