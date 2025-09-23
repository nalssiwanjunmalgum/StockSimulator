package com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence;

import com.portfolio2025.first.legacy.domain.Portfolio;

public interface LoadPortfolioPort {
    Portfolio get(Long portfolioId, Long userId);
}
