package com.portfolio2025.first.exception;

// 재시도 해야 하는 경우 -> throw (재시도할 수 있도록 해야함) + ACK 금지
public class RetryableMatchException extends RuntimeException {
    public RetryableMatchException(String message) {
        super(message);
    }

    public RetryableMatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
