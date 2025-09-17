package com.portfolio2025.first.legacy.exception;

// 재시도하지 않는 경우 (ACK + DLT)
public class NonRetryableException extends RuntimeException {
    public NonRetryableException(String message) {
        super(message);
    }

    public NonRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
