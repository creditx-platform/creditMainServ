package com.creditx.main.util;

import com.creditx.main.constants.EventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventValidationUtilsTest {

    @Test
    void validateEventType_shouldReturnTrueWhenEventTypeMatches() {
        // Given: Message with correct event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put(EventTypes.EVENT_TYPE_HEADER, EventTypes.HOLD_CREATED);
        Message<String> message = new GenericMessage<>("test payload", new MessageHeaders(headers));

        // When: Validating event type
        boolean result = EventValidationUtils.validateEventType(message, EventTypes.HOLD_CREATED);

        // Then: Validation should pass
        assertThat(result).isTrue();
    }

    @Test
    void validateEventType_shouldReturnFalseWhenEventTypeMismatch() {
        // Given: Message with different event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put(EventTypes.EVENT_TYPE_HEADER, EventTypes.HOLD_CREATED);
        Message<String> message = new GenericMessage<>("test payload", new MessageHeaders(headers));

        // When: Validating against different event type
        boolean result = EventValidationUtils.validateEventType(message, EventTypes.HOLD_EXPIRED);

        // Then: Validation should fail
        assertThat(result).isFalse();
    }

    @Test
    void validateEventType_shouldReturnFalseWhenEventTypeHeaderMissing() {
        // Given: Message without event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        Message<String> message = new GenericMessage<>("test payload", new MessageHeaders(headers));

        // When: Validating event type
        boolean result = EventValidationUtils.validateEventType(message, EventTypes.HOLD_CREATED);

        // Then: Validation should fail
        assertThat(result).isFalse();
    }

    @Test
    void validateEventType_shouldReturnFalseWhenMessageIsNull() {
        // When: Validating null message
        boolean result = EventValidationUtils.validateEventType(null, EventTypes.HOLD_CREATED);

        // Then: Validation should fail
        assertThat(result).isFalse();
    }

    @Test
    void validateEventType_shouldReturnFalseWhenExpectedEventTypeIsNull() {
        // Given: Valid message
        Map<String, Object> headers = new HashMap<>();
        headers.put(EventTypes.EVENT_TYPE_HEADER, EventTypes.HOLD_CREATED);
        Message<String> message = new GenericMessage<>("test payload", new MessageHeaders(headers));

        // When: Validating against null expected event type
        boolean result = EventValidationUtils.validateEventType(message, null);

        // Then: Validation should fail
        assertThat(result).isFalse();
    }

    @Test
    void validateEventType_shouldReturnFalseWhenExpectedEventTypeIsEmpty() {
        // Given: Valid message
        Map<String, Object> headers = new HashMap<>();
        headers.put(EventTypes.EVENT_TYPE_HEADER, EventTypes.HOLD_CREATED);
        Message<String> message = new GenericMessage<>("test payload", new MessageHeaders(headers));

        // When: Validating against empty expected event type
        boolean result = EventValidationUtils.validateEventType(message, "");

        // Then: Validation should fail
        assertThat(result).isFalse();
    }

    @Test
    void getEventType_shouldReturnEventTypeWhenPresent() {
        // Given: Message with event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put(EventTypes.EVENT_TYPE_HEADER, EventTypes.TRANSACTION_AUTHORIZED);
        Message<String> message = new GenericMessage<>("test payload", new MessageHeaders(headers));

        // When: Getting event type
        String eventType = EventValidationUtils.getEventType(message);

        // Then: Should return correct event type
        assertThat(eventType).isEqualTo(EventTypes.TRANSACTION_AUTHORIZED);
    }

    @Test
    void getEventType_shouldReturnNullWhenEventTypeHeaderMissing() {
        // Given: Message without event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        Message<String> message = new GenericMessage<>("test payload", new MessageHeaders(headers));

        // When: Getting event type
        String eventType = EventValidationUtils.getEventType(message);

        // Then: Should return null
        assertThat(eventType).isNull();
    }

    @Test
    void getEventType_shouldReturnNullWhenMessageIsNull() {
        // When: Getting event type from null message
        String eventType = EventValidationUtils.getEventType(null);

        // Then: Should return null
        assertThat(eventType).isNull();
    }
}
