package com.portfolio2025.first.refactor.phase_A.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio2025.first.refactor.phase_A.orders.adapter.in.web.CreateOrderRequest;
import com.portfolio2025.first.refactor.phase_A.orders.adapter.in.web.CreateOrderResponse;
import com.portfolio2025.first.refactor.phase_A.orders.adapter.in.web.OrdersController;
import com.portfolio2025.first.refactor.phase_A.orders.adapter.in.web.OrderCommandService;
import com.portfolio2025.first.refactor.phase_A.shared.api.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrdersController.class)
@Import(GlobalExceptionHandler.class)
class OrdersControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoBean
    OrderCommandService orderCommandService;


    @Test
    void create_limit_success_201() throws Exception {
        // given
        CreateOrderRequest req = CreateOrderRequest.builder()
                .portfolioId(1L)
                .stockCode("005930")
                .side(CreateOrderRequest.Side.BUY)
                .priceType(CreateOrderRequest.PriceType.LIMIT)
                .quantity(10L)
                .limitPrice(71200L)
                .build();

        CreateOrderResponse res = CreateOrderResponse.builder()
                .orderId(12345L)
                .status("CREATED")
                .acceptedAt(OffsetDateTime.parse("2025-09-16T22:20:05+09:00"))
                .summary(CreateOrderResponse.Summary.builder()
                        .portfolioId(1L)
                        .stockCode("005930")
                        .side(CreateOrderRequest.Side.BUY)
                        .priceType(CreateOrderRequest.PriceType.LIMIT)
                        .quantity(10L)
                        .limitPrice(71200L)
                        .estimatedTotalPrice(712000L)
                        .build())
                .links(CreateOrderResponse.Links.builder()
                        .self("/api/v1/orders/12345")
                        .cancel("/api/v1/orders/12345/cancel")
                        .build())
                .build();

        Mockito.when(orderCommandService.create(any(CreateOrderRequest.class), any()))
                .thenReturn(new OrderCommandService.CreateResult(res, true));

        // when/then
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-1")
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/12345"))
                .andExpect(jsonPath("$.orderId").value(12345))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.summary.limitPrice").value(71200))
                .andExpect(jsonPath("$.summary.estimatedTotalPrice").value(712000));
    }

    @Test
    void create_market_with_limitPrice_should_400() throws Exception {
        // given: MARKET인데 limitPrice를 보낸 잘못된 요청(AssertTrue 조건 위반)
        CreateOrderRequest bad = CreateOrderRequest.builder()
                .portfolioId(1L)
                .stockCode("005930")
                .side(CreateOrderRequest.Side.BUY)
                .priceType(CreateOrderRequest.PriceType.MARKET)
                .quantity(10L)
                .limitPrice(1L) // 금지
                .build();

        // when/then
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", containsString("Invalid request")));
        // details 배열은 구현(필드/오브젝트 에러 수집 방식)에 따라 비어있을 수 있음
    }

    @Test
    void create_with_same_idempotencyKey_returns_200_same_order() throws Exception {
        // given
        CreateOrderRequest req = CreateOrderRequest.builder()
                .portfolioId(1L)
                .stockCode("005930")
                .side(CreateOrderRequest.Side.BUY)
                .priceType(CreateOrderRequest.PriceType.LIMIT)
                .quantity(10L)
                .limitPrice(71200L)
                .build();

        CreateOrderResponse res = CreateOrderResponse.builder()
                .orderId(555L)
                .status("CREATED")
                .acceptedAt(OffsetDateTime.parse("2025-09-16T22:30:00+09:00"))
                .summary(CreateOrderResponse.Summary.builder()
                        .portfolioId(1L)
                        .stockCode("005930")
                        .side(CreateOrderRequest.Side.BUY)
                        .priceType(CreateOrderRequest.PriceType.LIMIT)
                        .quantity(10L)
                        .limitPrice(71200L)
                        .estimatedTotalPrice(712000L)
                        .build())
                .links(CreateOrderResponse.Links.builder()
                        .self("/api/v1/orders/555")
                        .cancel("/api/v1/orders/555/cancel")
                        .build())
                .build();

        Mockito.when(orderCommandService.create(any(CreateOrderRequest.class), eq("idem-dup")))
                .thenReturn(new OrderCommandService.CreateResult(res, true))   // 1st call → 201
                .thenReturn(new OrderCommandService.CreateResult(res, false)); // 2nd call → 200

        // 1) 최초 요청 → 201
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-dup")
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/555"))
                .andExpect(jsonPath("$.orderId").value(555));

        // 2) 동일 키 재요청 → 200, 동일 Location/바디
        mvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "idem-dup")
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(header().string("Location", "/api/v1/orders/555"))
                .andExpect(jsonPath("$.orderId").value(555));
    }
}
