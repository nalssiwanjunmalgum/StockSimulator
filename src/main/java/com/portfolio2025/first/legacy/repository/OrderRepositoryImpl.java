package com.portfolio2025.first.legacy.repository;

import com.portfolio2025.first.legacy.domain.Order;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepositoryImpl extends BaseRepositoryImpl<Order, Long> implements OrderRepository{
    public OrderRepositoryImpl(EntityManager em) {
        super(em, Order.class);
    }

    /** 부모 엔티티 조회시 JOIN FETCH - N+1 방지하기 위함 **/
    @Override
    public Optional<Order> findByIdWithStockOrders(Long orderId) {
        List<Order> result = em.createQuery(
                        "SELECT DISTINCT o FROM Order o " +
                                "JOIN FETCH o.stockOrders so " +
                                "JOIN FETCH so.stock " +
                                "WHERE o.id = :orderId", Order.class)
                .setParameter("orderId", orderId)
                .getResultList();

        return result.stream().findFirst();
    }

    // 너무 과하게 한건 아닐까 하는 생각을 해야 한다
    @Override
    public Optional<Order> findWithDetailsById(Long orderId) {
        List<Order> result = em.createQuery(
                        "SELECT DISTINCT o FROM Order o " +
                                "LEFT JOIN FETCH o.stockOrders so " +   // 컬렉션
                                "LEFT JOIN FETCH so.stock s " +          // 단일
                                "LEFT JOIN FETCH o.portfolio p " +       // 단일(있다면)
                                "LEFT JOIN FETCH p.user u " +            // 단일(있다면)
                                // "LEFT JOIN FETCH o.payments pay "     // 추가 컬렉션이 있다면 주의: 컬렉션이 여러 개면 카티전 폭발 가능
                                "WHERE o.id = :orderId", Order.class)
                .setParameter("orderId", orderId)
                // 하이버네이트 사용 시 DISTINCT 중복 방지 최적화
                .setHint("hibernate.query.passDistinctThrough", false)
                .setHint("org.hibernate.readOnly", true)
                .getResultList();

        return result.stream().findFirst();
    }
}
