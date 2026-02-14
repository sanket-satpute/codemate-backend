package com.codescope.backend.analysisjob.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedJobAccessException extends RuntimeException {
    public UnauthorizedJobAccessException(String message) {
        super(message);
    }

    public UnauthorizedJobAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
