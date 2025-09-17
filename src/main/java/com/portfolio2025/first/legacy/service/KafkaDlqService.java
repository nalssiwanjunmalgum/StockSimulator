package com.portfolio2025.first.legacy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 주문 생성 시 중간에 예외가 발생한 경우 DLQ 처리 - KafkaDlqService
 * Parse error or processing error 상황.
 * [07.30]
 *
 * [고민]
 * DQL 토픽 별로 구분해서 진행하는 방식도 가능함 - 인터페이스로 공통 기능을 추상화 하는 방식은 어떨까?
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDlqService {

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String DLQ_TOPIC = "invalid.order.created";

    /**
     * JSON 파싱 실패 시 호출
     */
    public void sendParseError(String topic, String rawMessage, Exception e) {
        sendToDlq("parse_error", topic, rawMessage, e);
    }

    /**
     * 처리 중 예외 발생 시 호출
     */
    public void sendProcessingError(String topic, String rawMessage, Exception e) {
        sendToDlq("processing_error", topic, rawMessage, e);
    }

    /**
     * 공통 DLQ 전송 처리
     */
    private void sendToDlq(String errorType, String originalTopic, String message, Exception e) {
        Map<String, Object> payload = Map.of(
                "type", errorType,
                "topic", originalTopic,
                "message", message,
                "error", e.getMessage()
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            stringKafkaTemplate.send(DLQ_TOPIC, json);
            log.warn("📦 DLQ 전송 완료: {}", json);
        } catch (JsonProcessingException ex) {
            log.error("❌ DLQ 직렬화 실패", ex);
        } catch (Exception ex) {
            log.error("❌ DLQ 전송 실패", ex);
        }
    }
}
