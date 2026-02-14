package com.codescope.backend.ai.client;

import com.codescope.backend.ai.model.FileChunk;
import com.codescope.backend.ai.model.LLMChunkResponse;

public interface ILLMClient {
    LLMChunkResponse performAnalysis(FileChunk chunk);
}
