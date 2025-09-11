package com.portfolio2025.first.exception;

// 이미 처리된 경우 (멱등 HIT 한 경우) -> ACK
public class AlreadyProcessedException extends RuntimeException {
    public AlreadyProcessedException(String message) {
        super(message);
    }
}
