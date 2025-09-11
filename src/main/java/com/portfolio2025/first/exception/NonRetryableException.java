package com.portfolio2025.first.exception;

// 재시도하지 않는 경우 (ACK + DLT)
public class NonRetryableMatchException extends RuntimeException {
    public NonRetryableMatchException(String message) {
        super(message);
    }

    public NonRetryableMatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
