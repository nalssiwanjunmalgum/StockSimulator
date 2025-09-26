CREATE TABLE idempotency_keys (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT        NOT NULL,
    portfolio_id     BIGINT        NOT NULL,
    client_order_id  VARCHAR(128)  NOT NULL,
    payload_hash     VARCHAR(128)  NULL,
    status           VARCHAR(16)   NOT NULL, -- PENDING / COMPLETED / FAILED
    order_id         BIGINT        NULL,
    stock_order_id   BIGINT        NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP     NULL,
    CONSTRAINT ux_idemp UNIQUE (user_id, portfolio_id, client_order_id)
);

-- 조회 최적화(선택)
CREATE INDEX ix_idemp_user_portfolio ON idempotency_keys (user_id, portfolio_id);
CREATE INDEX ix_idemp_status ON idempotency_keys (status);
