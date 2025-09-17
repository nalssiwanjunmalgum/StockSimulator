package com.portfolio2025.first.legacy.config;

import com.portfolio2025.first.legacy.exception.NonRetryableException;
import com.portfolio2025.first.legacy.exception.PayloadParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
    stringKafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {

        var f = new ConcurrentKafkaListenerContainerFactory<String, String>();
        f.setConsumerFactory(consumerFactory);

        // 수동 즉시 커밋 (yml에서도 지정했지만 명시)
        f.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // 에러 핸들러: 200ms × 3회 재시도, NonRetryable은 즉시 중단
        var backoff = new FixedBackOff(200L, 3L);
        var eh = new DefaultErrorHandler(backoff);
        eh.addNotRetryableExceptions(
                NonRetryableException.class,
                PayloadParseException.class
        );
        f.setCommonErrorHandler(eh);

        return f;
    }
}
