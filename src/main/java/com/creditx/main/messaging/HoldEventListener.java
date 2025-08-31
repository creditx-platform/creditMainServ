package com.creditx.main.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.service.HoldEventService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class HoldEventListener {

    private final HoldEventService holdEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public Consumer<String> holdCreated() {
        return payload -> {
            try {
                log.info("Received hold.created event: {}", payload);
                HoldCreatedEvent event = objectMapper.readValue(payload, HoldCreatedEvent.class);
                holdEventService.processHoldCreated(event);
                log.info("Successfully processed hold.created for transaction: {}", event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to process hold.created event: {}", payload, e);
                throw new RuntimeException("Failed to process hold.created event", e);
            }
        };
    }
}
