package com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency;

public interface CheckIdempotencyPort {
    record Existing(Long orderId, Long stockOrderId) {}
    void record(String clientOrderId, Long orderId, Long stockOrderId);
    Existing findExistingByClientOrderId(String clientOrderId);

}
