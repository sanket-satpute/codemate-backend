package com.codescope.backend.ai;

import java.util.Map;

public interface LLMClient {
    String sendPrompt(String prompt);
    String sendJSONPrompt(Map<String, Object> data);
}
