package com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence;

import com.portfolio2025.first.legacy.domain.Order;

public interface SaveOrderPort {
    Long saveNewOrder(Order order);
}
