package com.portfolio2025.first.legacy.repository;

import com.portfolio2025.first.legacy.domain.Portfolio;
import com.portfolio2025.first.legacy.domain.PortfolioStock;
import com.portfolio2025.first.legacy.domain.stock.Stock;
import java.util.Optional;

public interface PortfolioStockRepository extends BaseRepository<PortfolioStock, Long> {
    // 추가 기능
    Optional<PortfolioStock> findByPortfolioAndStockWithLock(Portfolio portfolio, Stock stock);

    Optional<PortfolioStock> findByPortfolioAndStock(Portfolio portfolio, Stock stock);
}
