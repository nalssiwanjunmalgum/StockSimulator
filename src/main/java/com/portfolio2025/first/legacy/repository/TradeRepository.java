package com.portfolio2025.first.legacy.repository;

import com.portfolio2025.first.legacy.domain.Trade;

public interface TradeRepository extends BaseRepository<Trade, Long> {
    boolean existsByBuyOrderAndSellOrder(Long buyOrderId, Long sellOrderId);
}
