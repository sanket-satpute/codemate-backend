package com.codescope.backend.chat.exception;

public class InvalidMessageException extends ChatException {
    public InvalidMessageException(String message) {
        super(message);
    }
}
