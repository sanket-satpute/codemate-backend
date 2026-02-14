package com.codescope.backend.ai;

import reactor.core.publisher.Mono;

public interface LLMService {
    Mono<String> queryModel(String input);
}
