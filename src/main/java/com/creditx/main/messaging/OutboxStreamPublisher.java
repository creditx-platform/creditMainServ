package com.creditx.main.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxStreamPublisher {

    private final StreamBridge streamBridge;

    @Value("${app.outbox.binding}")
    private String bindingName;

    public void publish(String key, String payload) {

        Message<String> message = MessageBuilder
                .withPayload(payload)
                .setHeader("key", key)
                .build();

        streamBridge.send(bindingName, message);
    }
}
