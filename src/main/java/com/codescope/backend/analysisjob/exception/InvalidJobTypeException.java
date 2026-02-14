package com.codescope.backend.analysisjob.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidJobTypeException extends RuntimeException {
    public InvalidJobTypeException(String message) {
        super(message);
    }

    public InvalidJobTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
