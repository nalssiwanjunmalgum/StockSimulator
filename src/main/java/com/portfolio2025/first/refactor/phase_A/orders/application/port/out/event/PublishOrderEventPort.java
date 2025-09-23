package com.portfolio2025.first.refactor.phase_A.orders.application.port.out.event;

import java.time.Instant;

public interface PublishOrderEventPort {
    void publishAccepted(Long orderId, Long stockOrderId, Instant acceptedAt, String requestId);
}
