package com.creditx.main.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxStreamPublisher {

    private final StreamBridge streamBridge;

    @Value("${app.outbox.binding}")
    private String bindingName;

    public void publish(String key, String payload) {
        log.debug("Publishing message to binding '{}' with key: {}", bindingName, key);

        Message<String> message = MessageBuilder
                .withPayload(payload)
                .setHeader("key", key)
                .build();

        try {
            streamBridge.send(bindingName, message);
            log.debug("Successfully published message with key: {}", key);
        } catch (Exception e) {
            log.error("Failed to publish message with key {}: {}", key, e.getMessage(), e);
            throw e;
        }
    }
}
