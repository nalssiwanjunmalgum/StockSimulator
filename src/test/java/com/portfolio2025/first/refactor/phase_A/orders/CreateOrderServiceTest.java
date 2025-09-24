package com.portfolio2025.first.refactor.phase_A.orders;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portfolio2025.first.legacy.domain.Portfolio;
import com.portfolio2025.first.legacy.domain.PortfolioStock;
import com.portfolio2025.first.legacy.domain.PortfolioType;
import com.portfolio2025.first.legacy.domain.User;
import com.portfolio2025.first.legacy.domain.stock.Stock;
import com.portfolio2025.first.legacy.domain.stock.StockOrder;
import com.portfolio2025.first.legacy.domain.stock.StockOrderStatus;
import com.portfolio2025.first.legacy.domain.vo.Money;
import com.portfolio2025.first.legacy.domain.vo.Quantity;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand.OrderSide;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand.OrderType;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreateOrderCommand.TimeInForce;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreatedOrderResult;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.in.CreateOrderUseCase.CreatedOrderResult.OrderAcceptStatus;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.event.PublishOrderEventPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.idempotency.CheckIdempotencyPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.LoadPortfolioPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.LoadStockPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.SaveOrderPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.port.out.persistence.SaveStockOrderPort;
import com.portfolio2025.first.refactor.phase_A.orders.application.service.CreateOrderService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    @Mock LoadPortfolioPort loadPortfolioPort;
    @Mock LoadStockPort loadStockPort;
    @Mock SaveOrderPort saveOrderPort;
    @Mock SaveStockOrderPort saveStockOrderPort;
    @Mock CheckIdempotencyPort checkIdempotencyPort;
    @Mock PublishOrderEventPort publishOrderEventPort;

    @InjectMocks CreateOrderService sut;

    @BeforeEach
    void setUp() {
//        MockitoAnnotations.openMocks(this); // TEST 시작 전 @Mock 필드 초기화 (Mockito 확장 직접 호출)
    }

    // ===== 헬퍼: 도메인 생성기 =====
    private User user(long id, String name) {
        User u = User.createUser(name, "Seoul", "010-0000-0000", "foo@bar.com", "uid-"+id);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Portfolio portfolioWithCash(User u, long cash) {
        Portfolio p = Portfolio.createPortfolio(u, PortfolioType.STOCK);
        if (cash > 0) p.deposit(new Money(cash));
        return p;
    }


    private Stock stock(long id, String name, long price) {
        Stock s = Stock.createStock(null, new Money(price), name, "S"+id, new Quantity(1_000_000L));
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private PortfolioStock holding(Portfolio p, Stock s, long qty, long avgPrice) {
        PortfolioStock ps = PortfolioStock.createPortfolioStock(
                p, s, new Quantity(qty), new Money(avgPrice)
        );

        p.getPortfolioStocks().add(ps);
        return ps;
    }

    // 제안해서 구매하려는 Command (매수)
    private CreateOrderCommand limitBuyCmd(long userId, long portfolioId, String stockCode, long qty, long limit) {
        return CreateOrderCommand.builder()
                .userId(userId)
                .portfolioId(portfolioId)
                .stockCode(stockCode)
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .quantity(new BigDecimal(qty))
                .limitPrice(new BigDecimal(limit))
                .tif(TimeInForce.GTC)
                .clientOrderId("idem-LB-" + portfolioId)
                .requestId("req-LB-" + portfolioId)
                .build();
    }

    // 시장가 매도
    private CreateOrderCommand marketSellCmd(long userId, long portfolioId, String stockCode, long qty) {
        return CreateOrderCommand.builder()
                .userId(userId)
                .portfolioId(portfolioId)
                .stockCode(stockCode)
                .side(OrderSide.SELL)
                .orderType(OrderType.MARKET)
                .quantity(new BigDecimal(qty))
                .limitPrice(null)
                .tif(TimeInForce.IOC)
                .clientOrderId("idem-MS-" + portfolioId)
                .requestId("req-MS-" + portfolioId)
                .build();
    }

    @Test
    @DisplayName("1) LIMIT BUY 정상 접수 → 현금 예약/저장/이벤트 호출, ACCEPTED")
    void limitBuyAccepted() {

        // Given: 도메인 픽스처
        User u = user(1, "alice");
        Portfolio pf = portfolioWithCash(u, 10_000_000L);
        Stock st = stock(100, "ACME", 1_000L);

        // 멱등/조회 포트 스텁
        when(checkIdempotencyPort.findExistingByClientOrderId("idem-LB-10")).thenReturn(null);
        when(loadPortfolioPort.get(10L, 1L)).thenReturn(pf);
        when(loadStockPort.getFromStockCode("ACME")).thenReturn(st);

        // 저장 포트 스텁 -> 생성된 Order, StockOrder에 대한 stub
        when(saveOrderPort.saveNewOrder(any())).thenReturn(111L);
        when(saveStockOrderPort.saveNewStockOrder(any())).thenReturn(222L);

        // 커맨드
        CreateOrderCommand cmd = limitBuyCmd(1L, 10L, "ACME", 5L, 1_000L);

        // When
        CreatedOrderResult res = sut.createOrder(cmd);

        // Then: 결과 검증
        assertThat(res.getStatus()).isEqualTo(OrderAcceptStatus.ACCEPTED);
        assertThat(res.getOrderId()).isEqualTo(111L);
        assertThat(res.getStockOrderId()).isEqualTo(222L);
        assertThat(res.getAcceptedAt()).isNotNull();

        // 멱등 기록/이벤트 발행 검증 (헬퍼가 만든 키와 일치)
        verify(checkIdempotencyPort).record("idem-LB-10", 111L, 222L);
        verify(publishOrderEventPort).publishAccepted(eq(111L), eq(222L), any(Instant.class), eq("req-LB-10"));

        // 조회 포트 호출 검증
        verify(loadPortfolioPort).get(10L, 1L);
        verify(loadStockPort).getFromStockCode("ACME");

        // 저장 포트 호출 검증 (시그니처 맞춤)
        verify(saveOrderPort).saveNewOrder(any());

        // 캡처해서 내부 값까지 검증
        ArgumentCaptor<StockOrder> soCaptor = ArgumentCaptor.forClass(StockOrder.class);
        verify(saveStockOrderPort).saveNewStockOrder(soCaptor.capture());
        StockOrder so = soCaptor.getValue();

        System.out.println(so.getStock());

        // 캡처한 StockOrder의 핵심 필드 검증 (도메인 모델에 맞춰 이름 조정)
        assertThat(so.getStock()).isSameAs(st);
        assertThat(so.getRequestedQuantity().getQuantityValue()).isEqualTo(5L);
        assertThat(so.getRequestedPrice().getMoneyValue()).isEqualTo(1_000L);
        assertThat(so.getStockOrderStatus()).isEqualTo(StockOrderStatus.PENDING); // 초기 상태 기대값

        // (선택) 호출 순서까지 보장하고 싶다면:
        // InOrder inOrder = inOrder(checkIdempotencyPort, loadPortfolioPort, loadStockPort, saveOrderPort, saveStockOrderPort, publishOrderEventPort);
        // inOrder.verify(checkIdempotencyPort).findExistingByClientOrderId("idem-LB-10");
        // inOrder.verify(loadPortfolioPort).get(10L, 1L);
        // inOrder.verify(loadStockPort).get(100L);
        // inOrder.verify(saveOrderPort).saveNewOrder(any());
        // inOrder.verify(saveStockOrderPort).saveNewStockOrder(any());
        // inOrder.verify(checkIdempotencyPort).record("idem-LB-10", 111L, 222L);
        // inOrder.verify(publishOrderEventPort).publishAccepted(eq(111L), eq(222L), any(Instant.class), eq("req-LB-10"));
    }

    @Test
    @DisplayName("2) MARKET SELL 정상 접수 → 보유 수량 예약/저장/이벤트, ACCEPTED")
    void marketSellAccepted() {
        // Given
        User u = user(2, "bob");
        Portfolio pf = portfolioWithCash(u, 0L);
        Stock st = stock(200, "BETA", 2_000L);
        holding(pf, st, 100L, 1_500L); // 보유 100주

        when(checkIdempotencyPort.findExistingByClientOrderId("idem-MS-20")).thenReturn(null);
        when(loadPortfolioPort.get(20L, 2L)).thenReturn(pf);
        when(loadStockPort.getFromStockCode("BETA")).thenReturn(st);
        when(saveOrderPort.saveNewOrder(any())).thenReturn(333L);
        when(saveStockOrderPort.saveNewStockOrder(any())).thenReturn(444L);

        CreateOrderCommand cmd = marketSellCmd(2L, 20L, "BETA", 30L);

        // When
        CreatedOrderResult res = sut.createOrder(cmd);

        // Then
        assertThat(res.getStatus()).isEqualTo(OrderAcceptStatus.ACCEPTED);
        assertThat(res.getOrderId()).isEqualTo(333L);
        assertThat(res.getStockOrderId()).isEqualTo(444L);

        verify(checkIdempotencyPort).record("idem-MS-20", 333L, 444L);
        verify(publishOrderEventPort).publishAccepted(eq(333L), eq(444L), any(Instant.class), eq("req-MS-20"));
    }

    @Test
    @DisplayName("3) clientOrderId 중복 → 저장 없이 DUPLICATE_IGNORED")
    void duplicateIgnored() {
        // Given
        var cmd = limitBuyCmd(1L, 10L, "ACME", 5L, 1_000L);
        when(checkIdempotencyPort.findExistingByClientOrderId("idem-LB-10"))
                .thenReturn(new CheckIdempotencyPort.Existing(777L, 888L));

        // When
        CreatedOrderResult res = sut.createOrder(cmd);

        // Then
        assertThat(res.getStatus()).isEqualTo(OrderAcceptStatus.DUPLICATE_IGNORED);
        assertThat(res.getOrderId()).isEqualTo(777L);
        assertThat(res.getStockOrderId()).isEqualTo(888L);

        verifyNoInteractions(loadPortfolioPort, loadStockPort, saveOrderPort, saveStockOrderPort, publishOrderEventPort);
    }

    @Test
    @DisplayName("4) SELL 보유 수량 부족(또는 보유 없음) → IllegalArgumentException")
    void sellInsufficientHoldings() {
        // Given: 보유 없음
        User u = user(9, "charlie");
        Portfolio pf = portfolioWithCash(u, 0L);
        Stock st = stock(900, "GAMMA", 10_000L);

//        when(checkIdempotencyPort.findExistingByClientOrderId(any())).thenReturn(null);
        when(loadPortfolioPort.get(90L, 9L)).thenReturn(pf);
        when(loadStockPort.getFromStockCode("GAMMA")).thenReturn(st);

        CreateOrderCommand cmd = CreateOrderCommand.builder()
                .userId(9L).portfolioId(90L).stockCode("GAMMA")
                .side(OrderSide.SELL).orderType(OrderType.LIMIT)
                .quantity(BigDecimal.valueOf(100L))
                .limitPrice(BigDecimal.valueOf(10_000L))
                .tif(TimeInForce.GTC)
                .build();

        // When / Then
        assertThatThrownBy(() -> sut.createOrder(cmd))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(saveOrderPort, saveStockOrderPort, publishOrderEventPort);
    }

    @Test
    @DisplayName("5) BUY 현금 부족 → IllegalArgumentException")
    void buyInsufficientCash() {
        // Given: 현금 0
        User u = user(1, "alice");
        Portfolio pf = portfolioWithCash(u, 4_000_000L);
        Stock st = stock(100, "ACME", 1_000_000L);

        when(checkIdempotencyPort.findExistingByClientOrderId("idem-LB-10")).thenReturn(null);
        when(loadPortfolioPort.get(10L, 1L)).thenReturn(pf);
        when(loadStockPort.getFromStockCode("ACME")).thenReturn(st);

        CreateOrderCommand
                cmd = limitBuyCmd(1L, 10L, "ACME", 5L, 1_000_000L); // 총 500만 → 현금 부족

        // When / Then
        assertThatThrownBy(() -> sut.createOrder(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현금").as("도메인 예외 메시지는 상황에 맞게 조정");

        verifyNoInteractions(saveOrderPort, saveStockOrderPort, publishOrderEventPort);
    }

    @Test
    @DisplayName("6) LIMIT인데 limitPrice 누락/<=0 → IllegalArgumentException")
    void invalidLimitPrice() {
        // NPE 관련 코드 어떻게?
        // 검증 책임에 대해서 생각할 수 있어야 한다 -> 검증 책임은 Request 그리고 Command에서 수행하는 방식으로

        // Given
        CreateOrderCommand cmdNoPrice = CreateOrderCommand.builder()
                .userId(1L).portfolioId(10L).stockCode("ACME")
                .side(OrderSide.BUY).orderType(OrderType.LIMIT)
                .quantity(new BigDecimal("5"))
                .tif(TimeInForce.GTC)
                .build();

        CreateOrderCommand cmdZero = limitBuyCmd(1L,
                10L, "ACME", 5L, 0L);

        // When / Then
        assertThatThrownBy(() -> sut.createOrder(cmdNoPrice))
                .isInstanceOf(IllegalArgumentException.class);

        // portfolio가 null 인 상황
        assertThatThrownBy(() -> sut.createOrder(cmdZero))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(saveOrderPort, saveStockOrderPort, publishOrderEventPort);
    }

}
