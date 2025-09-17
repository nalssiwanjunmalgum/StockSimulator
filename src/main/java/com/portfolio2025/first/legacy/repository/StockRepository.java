package com.portfolio2025.first.legacy.repository;

import com.portfolio2025.first.legacy.domain.stock.Stock;
import java.util.Optional;

public interface StockRepository extends BaseRepository<Stock, Long> {
    Optional<Stock> findByStockCode(String stockCode);
}
