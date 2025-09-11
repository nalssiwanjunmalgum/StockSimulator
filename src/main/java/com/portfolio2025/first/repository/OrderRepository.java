package com.portfolio2025.first.repository;

import com.portfolio2025.first.domain.Order;
import java.util.Optional;

public interface OrderRepository extends BaseRepository<Order, Long>{
    /** JOIN FETCH : Order and StockOrder**/
    Optional<Order> findByIdWithStockOrders(Long orderId);
    /**@Query("""
    select o from Order o
    left join fetch o.stockOrders so
    left join fetch so.stock s
    where o.id = :id
    """) **/
    public Optional<Order> findWithDetailsById(Long orderId);
}
