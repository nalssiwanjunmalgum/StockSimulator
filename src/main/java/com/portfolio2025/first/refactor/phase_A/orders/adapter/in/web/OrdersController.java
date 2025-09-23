package com.portfolio2025.first.refactor.phase_A.orders.adapter.in.web;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrdersController {

    private final OrderCommandService orderCommandService;

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request)
    {
        OrderCommandService.CreateResult result = orderCommandService.create(request, idempotencyKey);

        // Service 는 Context(맥락) 관련 정보를 제공하고, Controller에서 이를 해석하고 ResponseEntity를 구조화한다
        URI location = URI.create("/api/v1/orders/" + result.getResponse().getOrderId());

        if (result.isCreated()) {
            return ResponseEntity.created(location).body(result.getResponse()); // 201 + Location
        }

        return ResponseEntity.ok() // 200
                .header("Location", location.toString()) // 재시도(중복) 시에도 Location 에코
                .body(result.getResponse());
    }
}
