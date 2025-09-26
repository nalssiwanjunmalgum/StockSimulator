package com.portfolio2025.first.refactor.phase_A.orders.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints =
        @UniqueConstraint(name = "ux_idemp", columnNames = {"user_id","portfolio_id","client_order_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="portfolio_id", nullable=false)
    private Long portfolioId;

    @Column(name="client_order_id", nullable=false, length=128)
    private String clientOrderId;

    @Column(name="payload_hash", length=128)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable=false, length=16)
    private IdempotencyStatus status;

    @Column(name="order_id")
    private Long orderId;

    @Column(name="stock_order_id")
    private Long stockOrderId;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at", nullable=false)
    private LocalDateTime updatedAt;

    @Column(name="expires_at")
    private LocalDateTime expiresAt;

//    @Version
//    private Long version; // 낙관락(선택)

    @Builder
    private IdempotencyKey(Long userId, Long portfolioId, String clientOrderId,
                           String payloadHash, IdempotencyStatus status,
                           Long orderId, Long stockOrderId,
                           LocalDateTime createdAt, LocalDateTime updatedAt,
                           LocalDateTime expiresAt) {

        this.userId = Objects.requireNonNull(userId);
        this.portfolioId = Objects.requireNonNull(portfolioId);
        this.clientOrderId = Objects.requireNonNull(clientOrderId);
        this.payloadHash = payloadHash;
        this.status = Objects.requireNonNull(status);
        this.orderId = orderId;
        this.stockOrderId = stockOrderId;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.expiresAt = expiresAt;
    }

    public static IdempotencyKey pending(Long userId, Long portfolioId, String clientOrderId, String payloadHash,
                                         Duration ttl) {
        LocalDateTime now = LocalDateTime.now();
        return IdempotencyKey.builder()
                .userId(userId)
                .portfolioId(portfolioId)
                .clientOrderId(clientOrderId)
                .payloadHash(payloadHash)
                .status(IdempotencyStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .expiresAt(ttl != null ? now.plus(ttl) : null)
                .build();
    }

    public void complete(Long orderId, Long stockOrderId) {
        this.status = IdempotencyStatus.COMPLETED;
        this.orderId = orderId;
        this.stockOrderId = stockOrderId;
        this.updatedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = IdempotencyStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
    }
}
