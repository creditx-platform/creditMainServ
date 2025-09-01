package com.creditx.main.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.service.HoldEventService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class HoldEventListener {

    private final HoldEventService holdEventService;
    private final ObjectMapper objectMapper;
    
    public HoldEventListener(HoldEventService holdEventService) {
        this.holdEventService = holdEventService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Enable JSR310 module for Instant serialization
    }

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

    @Bean
    public Consumer<String> holdExpired() {
        return payload -> {
            try {
                log.info("Received hold.expired event: {}", payload);
                HoldExpiredEvent event = objectMapper.readValue(payload, HoldExpiredEvent.class);
                holdEventService.processHoldExpired(event);
                log.info("Successfully processed hold.expired for transaction: {}", event.getTransactionId());
            } catch (Exception e) {
                log.error("Failed to process hold.expired event: {}", payload, e);
                throw new RuntimeException("Failed to process hold.expired event", e);
            }
        };
    }
}
