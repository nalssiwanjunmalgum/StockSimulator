package com.portfolio2025.first.legacy.exception;

// 재시도 해야 하는 경우 -> throw (재시도할 수 있도록 해야함) + ACK 금지
public class RetryableException extends RuntimeException {
    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
