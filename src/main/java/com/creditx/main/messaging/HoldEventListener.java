package com.creditx.main.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.service.HoldEventService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Configuration
@Slf4j
public class HoldEventListener {

    private final HoldEventService holdEventService;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;
    
    public HoldEventListener(HoldEventService holdEventService, Tracer tracer) {
        this.holdEventService = holdEventService;
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Enable JSR310 module for Instant serialization
    }

    @Bean
    public Consumer<Message<String>> holdCreated() {
        return message -> {
            String payload = message.getPayload();
            String traceId = (String) message.getHeaders().get("X-Trace-Id");
            
            Span span = tracer.nextSpan()
                    .name("hold-created-listener")
                    .tag("service", "creditMainServ")
                    .tag("event.type", "hold.created");
                    
            if (traceId != null) {
                span.tag("trace.parent.id", traceId);
            }
            
            try {
                span.start();
                log.info("Received hold.created event: {}", payload);
                HoldCreatedEvent event = objectMapper.readValue(payload, HoldCreatedEvent.class);
                holdEventService.processHoldCreated(event);
                log.info("Successfully processed hold.created for transaction: {}", event.getTransactionId());
            } catch (Exception e) {
                span.tag("error", e.getMessage());
                log.error("Failed to process hold.created event: {}", payload, e);
                throw new RuntimeException("Failed to process hold.created event", e);
            } finally {
                span.end();
            }
        };
    }

    @Bean
    public Consumer<Message<String>> holdExpired() {
        return message -> {
            String payload = message.getPayload();
            String traceId = (String) message.getHeaders().get("X-Trace-Id");
            
            Span span = tracer.nextSpan()
                    .name("hold-expired-listener")
                    .tag("service", "creditMainServ")
                    .tag("event.type", "hold.expired");
                    
            if (traceId != null) {
                span.tag("trace.parent.id", traceId);
            }
            
            try {
                span.start();
                log.info("Received hold.expired event: {}", payload);
                HoldExpiredEvent event = objectMapper.readValue(payload, HoldExpiredEvent.class);
                holdEventService.processHoldExpired(event);
                log.info("Successfully processed hold.expired for transaction: {}", event.getTransactionId());
            } catch (Exception e) {
                span.tag("error", e.getMessage());
                log.error("Failed to process hold.expired event: {}", payload, e);
                throw new RuntimeException("Failed to process hold.expired event", e);
            } finally {
                span.end();
            }
        };
    }
}
