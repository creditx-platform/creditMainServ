package com.creditx.main.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxStreamPublisher {

  private final StreamBridge streamBridge;

  @Value("${app.outbox.binding}")
  private String bindingName;

  public void publish(String key, String payload, String eventType) {
    // Validate inputs
    if (key == null || payload == null || payload.trim().isEmpty()) {
      log.debug("Skipping publish - invalid key or payload. Key: {}, Payload: {}", key, payload);
      return;
    }
    if (eventType == null || eventType.trim().isEmpty()) {
      log.debug("Skipping publish - invalid eventType: {}", eventType);
      return;
    }

    log.debug("Publishing message to binding '{}' with key: {}", bindingName, key);

    Message<String> message = MessageBuilder.withPayload(payload).setHeader("key", key)
        .setHeader("eventType", eventType).build();

    try {
      streamBridge.send(bindingName, message);
      log.debug("Successfully published message with key: {}", key);
    } catch (Exception e) {
      log.error("Failed to publish message with key {}: {}", key, e.getMessage(), e);
      throw e;
    }
  }
}
