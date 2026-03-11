package com.codescope.backend.ai.client;

import com.codescope.backend.ai.model.FileChunk;
import com.codescope.backend.ai.model.LLMChunkResponse;

public interface ILLMClient {
    LLMChunkResponse performAnalysis(FileChunk chunk);

    reactor.core.publisher.Mono<String> chat(String prompt);

    reactor.core.publisher.Flux<String> chatStream(String prompt);
}
