package com.portfolio2025.first.integration;


import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.portfolio2025.first.consumer.MatchRequestConsumer;
import com.portfolio2025.first.consumer.OrderRequestConsumer;
import com.portfolio2025.first.dto.StockOrderRequestDTO;
import com.portfolio2025.first.dto.TransferToPortfolioRequestDTO;
import com.portfolio2025.first.repository.OrderRepository;
import com.portfolio2025.first.repository.PortfolioRepository;
import com.portfolio2025.first.repository.PortfolioStockRepository;
import com.portfolio2025.first.repository.StockRepository;
import com.portfolio2025.first.repository.TradeRepository;
import com.portfolio2025.first.repository.UserRepository;
import com.portfolio2025.first.service.AccountService;
import com.portfolio2025.first.service.BuyOrderProcessor;
import com.portfolio2025.first.service.KafkaProducerService;
import com.portfolio2025.first.service.RedisStockOrderService;
import com.portfolio2025.first.service.SellOrderProcessor;
import com.portfolio2025.first.service.StockOrderService;
import com.portfolio2025.first.support.IntegrationTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;


public class TradeIntegrationTest extends IntegrationTestSupport {

    @Autowired private StockOrderService stockOrderService;
    @Autowired private BuyOrderProcessor buyOrderProcessor;
    @Autowired private SellOrderProcessor sellOrderProcessor;
    @Autowired private RedisStockOrderService redisStockOrderService;

    @Autowired private StockRepository stockRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountService accountService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private PortfolioStockRepository portfolioStockRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoSpyBean private OrderRequestConsumer orderRequestConsumer;
    @MockitoSpyBean private MatchRequestConsumer matchRequestConsumer;
    @MockitoSpyBean private KafkaProducerService kafkaProducerService;

    @DynamicPropertySource
    static void overrideKafkaGroupId(DynamicPropertyRegistry r) {
        r.add("kafka.groups.order-prepare",
                () -> "order-prepare-group-test-" + UUID.randomUUID());
        r.add("kafka.groups.trade-match",
                () -> "trade-match-group-test-" + UUID.randomUUID());
        r.add("kafka.groups.redis-sync",
                () -> "redis-sync-group-test-" + UUID.randomUUID());
         r.add("kafka.groups.order-dlq",
             () -> "order-dlq-group-test-" + UUID.randomUUID());
    }

    @Test
    @DisplayName("E2E: 매수/매도 → order.created → Redis 반영 → match.request → 체결")
    void e2e_buy_sell_match_success() throws InterruptedException {
        // 0) 사전 이체: buyerA(1L) → 포트폴리오(1L)에 200,000원
        accountService.transferFromAccountToPortfolio(
                1L, new TransferToPortfolioRequestDTO("111-222-333333", 1L, 200_000L));

        // 1) 주문 DTO
        var buy  = new StockOrderRequestDTO("005930", 2L, 100_000L, 1L); // buyerA
        var sell = new StockOrderRequestDTO("005930", 2L, 100_000L, 2L); // sellerA

        // 2) 주문 생성 (분산락 + 트랜잭션 + AFTER_COMMIT Kafka 발행)
        stockOrderService.placeSingleOrder(buy,  buyOrderProcessor);
        stockOrderService.placeSingleOrder(sell, sellOrderProcessor);

        // 3) 이벤트 소비 검증 — 우선 두 건의 order.created가 모두 소비되었는지 확인
        verify(orderRequestConsumer, timeout(5000).times(2))
                .consumeOrderCreated(anyString(), any());

        // 그 다음 match.request 발행/소비
        verify(kafkaProducerService, timeout(5000).atLeastOnce())
                .publishMatchRequest("005930");
        verify(matchRequestConsumer, timeout(5000).atLeastOnce())
                .consumeMatchRequest(eq("005930"), any());

        // 4) 최종 체결 결과 — Awaitility로 안정적으로 대기
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(tradeRepository.findAll().size()).isEqualTo(1)
        );

        // 5) 포트폴리오/보유 종목 검증
        var buyerPortfolio = portfolioRepository.findById(1L).orElseThrow();

        var buyerStock = portfolioStockRepository
                .findByPortfolioAndStock(
                        portfolioRepository.findById(1L).orElseThrow(),
                        stockRepository.findByStockCode("005930").orElseThrow()
                )
                .orElseThrow();

        assertThat(buyerStock.getPortfolioQuantity().getQuantityValue()).isEqualTo(2L);
        assertThat(buyerPortfolio.getAvailableCash().getMoneyValue()).isEqualTo(0L);

        var sellerPortfolio = portfolioRepository.findById(2L).orElseThrow();
        var sellerStock = portfolioStockRepository
                .findByPortfolioAndStock(
                        portfolioRepository.findById(2L).orElseThrow(),
                        stockRepository.findByStockCode("005930").orElseThrow()
                )
                .orElseThrow();

        assertThat(sellerStock.getPortfolioQuantity().getQuantityValue()).isEqualTo(8L);
        assertThat(sellerPortfolio.getAvailableCash().getMoneyValue()).isEqualTo(200_000L);
    }

}
