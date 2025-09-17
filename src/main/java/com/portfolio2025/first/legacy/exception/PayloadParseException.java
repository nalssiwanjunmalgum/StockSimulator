package com.portfolio2025.first.legacy.exception;

// Parsing 실패한 경우 -> ACK + DLT
public class PayloadParseException extends RuntimeException {
    public PayloadParseException(String message) {
        super(message);
    }

    public PayloadParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
