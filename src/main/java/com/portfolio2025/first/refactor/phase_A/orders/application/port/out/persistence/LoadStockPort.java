package com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence;

import com.portfolio2025.first.legacy.domain.stock.Stock;

public interface LoadStockPort {
    Stock get(Long stockId);
}
