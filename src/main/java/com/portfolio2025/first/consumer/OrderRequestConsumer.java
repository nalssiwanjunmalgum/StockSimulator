package com.portfolio2025.first.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio2025.first.OrderValidator;
import com.portfolio2025.first.RedisRegister;
import com.portfolio2025.first.domain.Order;
import com.portfolio2025.first.domain.order.OrderType;
import com.portfolio2025.first.domain.stock.StockOrder;
import com.portfolio2025.first.dto.event.OrderCreatedEvent;
import com.portfolio2025.first.exception.AlreadyProcessedException;
import com.portfolio2025.first.exception.NonRetryableException;
import com.portfolio2025.first.exception.PayloadParseException;
import com.portfolio2025.first.exception.RetryableException;
import com.portfolio2025.first.service.KafkaDlqService;
import com.portfolio2025.first.service.KafkaProducerService;
import com.portfolio2025.first.service.OrderPrepareService;
import com.portfolio2025.first.service.RedisStockOrderService;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 주문 생성 이벤트 소비를 담당하는 OrderRequestConsumer
 *
 * [07.30]
 * (추가) initStrategyMap - 초기화 전 미리 주입하면 의존성 문제 발생으로 PostConstruct 활용..
 *
 * [고민]
 * 재시도 + DLQ + Idempotency 방지하는 설계로 진행하기
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderRequestConsumer {

    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final OrderValidator orderValidator;
    private final RedisRegister redisRegister;
    private final KafkaDlqService kafkaDlqService;
    private final RedisStockOrderService redisStockOrderService;
    private final OrderPrepareService orderPrepareService;

    private Map<OrderType, Consumer<StockOrder>> redisPushStrategy;

    @PostConstruct
    public void initStrategyMap() {
        redisPushStrategy = Map.of(
                OrderType.BUY, redisStockOrderService::pushBuyOrder,
                OrderType.SELL, redisStockOrderService::pushSellOrder
        );
    }

    @KafkaListener(
            topics = "order.created",
            groupId = "${kafka.groups.order-prepare}",
            containerFactory = "stringKafkaListenerContainerFactory" // AckMode=MANUAL_IMMEDIATE
    )
    public void consumeOrderCreated(String message, Acknowledgment ack) throws InterruptedException {
        try {
            // 1) 파싱 (깨지면 NonRetryable)
            OrderCreatedEvent event = parseEventOrThrow(message);
            // 2) 멱등 (키 기반) — 이미 처리됨이면 ACK 후 종료
            if (redisRegister.isAlreadyProcessed(event.getOrderId())) {
                log.warn("🔁 Already processed orderId={}", event.getOrderId());
                ack.acknowledge();
                return;
            }
            // 3) 주문 조회/검증 (일시 장애면 Retryable, 규칙 위반이면 NonRetryable)
            Order order = orderPrepareService.fetchAndValidateOrThrow(event);
            // 4) 처리 (Redis 등록 및 후속 발행) — 내부에서 일시 장애면 Retryable 던지기
            processOrderOrThrow(order, event);
            // 5) 멱등 마킹 (원자적 마킹 실패가 중복이라면 NonRetryable로 흡수 or Retryable 정책 선택)
            redisRegister.tryMarkProcessedOrThrow(order.getId());

            // ✅ 성공 시 ACK
            ack.acknowledge();

        } catch (AlreadyProcessedException e) {
            log.warn("🔁 Already processed: {}", e.getMessage());
            ack.acknowledge();

        } catch (PayloadParseException | NonRetryableException e) {
            // DLT 전송 후 ACK
            kafkaDlqService.sendProcessingError("order.created", message, e);
            log.error("🚫 Non-retryable error, sent to DLT", e);
            ack.acknowledge();

        } catch (RetryableException e) {
            // ⏳ 재시도 필요 → ACK 금지 (컨테이너가 재전송)
            log.warn("⏳ Retryable error, will be redelivered", e);
            throw e;

        } catch (Exception e) {
            // 분류 못한 예외는 보수적으로 재시도 경로에 태움
            log.warn("⚠️ Unclassified error, will retry", e);
            throw e;
        }
    }

    private OrderCreatedEvent parseEventOrThrow(String message) {
        try {
            return objectMapper.readValue(message, OrderCreatedEvent.class);
        } catch (JsonProcessingException e) {
            throw new PayloadParseException("Bad payload", e); // NonRetryable
        }
    }


    private void processOrderOrThrow(Order order, OrderCreatedEvent event) {
        try {
            OrderType orderType = OrderType.valueOf(event.getOrderType());
            Consumer<StockOrder> redisPusher = redisPushStrategy.get(orderType);
            if (redisPusher == null) {
                throw new NonRetryableException("Unsupported order type: " + orderType);
            }

            // (중복 검증 제거: 여기서는 검증하지 않거나, 필요 시 간단 검증만)
            for (StockOrder so : order.getStockOrders()) {
                redisPusher.accept(so); // Redis push (여기서 던지는 예외를 아래 catch에서 매핑)
                kafkaProducerService.publishMatchRequest(event.getStockCode());
            }

        } catch (DataAccessResourceFailureException e) {
            // 네트워크/리소스 일시 장애 → 재시도 가치 有
            throw new RetryableException("Redis transient issue", e);

        } catch (DuplicateKeyException e) {
            // 중복 삽입(이미 동일한 엔트리 존재) → 멱등으로 흡수 가능
            throw new AlreadyProcessedException("Idempotent hit while pushing to Redis");

        } catch (IllegalArgumentException e) {
            // 스펙 위반/매핑 불가 등 → NonRetryable
            throw new NonRetryableException("Process order failed: " + e.getMessage(), e);

        } catch (RuntimeException e) {
            // 분류 어려우면 보수적으로 재시도
            throw new RetryableException("Unexpected processing failure", e);
        }
    }
}
