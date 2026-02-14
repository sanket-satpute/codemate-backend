package com.codescope.backend.ai.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PromptGenerationException extends RuntimeException {
    public PromptGenerationException(String message) {
        super(message);
    }

    public PromptGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
