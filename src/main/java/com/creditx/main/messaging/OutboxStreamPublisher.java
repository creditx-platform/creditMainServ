package com.creditx.main.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxStreamPublisher {

    private final StreamBridge streamBridge;
    private final Tracer tracer;

    @Value("${app.outbox.binding}")
    private String bindingName;

    public void publish(String key, String payload) {
        String traceId = null;
        String spanId = null;
        
        if (tracer.currentSpan() != null) {
            traceId = tracer.currentSpan().context().traceId();
            spanId = tracer.currentSpan().context().spanId();
        }
        
        Message<String> message = MessageBuilder
                .withPayload(payload)
                .setHeader("key", key)
                .setHeader("X-Trace-Id", traceId)
                .setHeader("X-Span-Id", spanId)
                .build();

        streamBridge.send(bindingName, message);
    }
}
