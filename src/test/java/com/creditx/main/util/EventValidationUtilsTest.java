package com.creditx.main.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import com.creditx.main.constants.EventTypes;

class EventValidationUtilsTest {

	@Test
	void shouldValidateCorrectEventType() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.setHeader(EventTypes.EVENT_TYPE_HEADER, EventTypes.TRANSACTION_AUTHORIZED)
				.build();

		// when
		boolean result = EventValidationUtils.validateEventType(message, EventTypes.TRANSACTION_AUTHORIZED);

		// then
		assertThat(result).isTrue();
	}

	@Test
	void shouldNotValidateIncorrectEventType() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.setHeader(EventTypes.EVENT_TYPE_HEADER, EventTypes.HOLD_CREATED)
				.build();

		// when
		boolean result = EventValidationUtils.validateEventType(message, EventTypes.TRANSACTION_AUTHORIZED);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void shouldNotValidateWhenEventTypeHeaderMissing() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.build(); // No event type header

		// when
		boolean result = EventValidationUtils.validateEventType(message, EventTypes.TRANSACTION_AUTHORIZED);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void shouldNotValidateNullMessage() {
		// given
		Message<String> nullMessage = null;

		// when
		boolean result = EventValidationUtils.validateEventType(nullMessage, EventTypes.TRANSACTION_AUTHORIZED);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void shouldNotValidateNullExpectedEventType() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.setHeader(EventTypes.EVENT_TYPE_HEADER, EventTypes.TRANSACTION_AUTHORIZED)
				.build();

		// when
		boolean result = EventValidationUtils.validateEventType(message, null);

		// then
		assertThat(result).isFalse();
	}

	@Test
	void shouldNotValidateEmptyExpectedEventType() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.setHeader(EventTypes.EVENT_TYPE_HEADER, EventTypes.TRANSACTION_AUTHORIZED)
				.build();

		// when
		boolean result = EventValidationUtils.validateEventType(message, "");

		// then
		assertThat(result).isFalse();
	}

	@Test
	void shouldNotValidateWhitespaceExpectedEventType() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.setHeader(EventTypes.EVENT_TYPE_HEADER, EventTypes.TRANSACTION_AUTHORIZED)
				.build();

		// when
		boolean result = EventValidationUtils.validateEventType(message, "   ");

		// then
		assertThat(result).isFalse();
	}

	@Test
	void shouldValidateWithCustomEventTypeHeader() {
		// given
		String payload = "{\"holdId\":456}";
		Map<String, Object> headers = new HashMap<>();
		headers.put(EventTypes.EVENT_TYPE_HEADER, "custom.event.type");
        
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.copyHeaders(headers)
				.build();

		// when
		boolean result = EventValidationUtils.validateEventType(message, "custom.event.type");

		// then
		assertThat(result).isTrue();
	}

	@Test
	void shouldGetEventTypeFromMessage() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.setHeader(EventTypes.EVENT_TYPE_HEADER, EventTypes.HOLD_EXPIRED)
				.build();

		// when
		String eventType = EventValidationUtils.getEventType(message);

		// then
		assertThat(eventType).isEqualTo(EventTypes.HOLD_EXPIRED);
	}

	@Test
	void shouldReturnNullWhenEventTypeHeaderMissing() {
		// given
		String payload = "{\"transactionId\":123}";
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.build(); // No event type header

		// when
		String eventType = EventValidationUtils.getEventType(message);

		// then
		assertThat(eventType).isNull();
	}

	@Test
	void shouldReturnNullForNullMessage() {
		// given
		Message<String> nullMessage = null;

		// when
		String eventType = EventValidationUtils.getEventType(nullMessage);

		// then
		assertThat(eventType).isNull();
	}

	@Test
	void shouldHandleNonStringEventTypeHeader() {
		// given
		String payload = "{\"transactionId\":123}";
		Map<String, Object> headers = new HashMap<>();
		headers.put(EventTypes.EVENT_TYPE_HEADER, 12345); // Non-string value
        
		Message<String> message = MessageBuilder
				.withPayload(payload)
				.copyHeaders(headers)
				.build();

		// when
		String eventType = EventValidationUtils.getEventType(message);
		boolean isValid = EventValidationUtils.validateEventType(message, "12345");

		// then
		assertThat(eventType).isEqualTo("12345"); // Should convert to string
		assertThat(isValid).isTrue(); // Should validate correctly
	}

	@Test
	void shouldValidateAllEventTypes() {
		// Test validation for all defined event types
		String[] allEventTypes = {
			EventTypes.TRANSACTION_AUTHORIZED,
			EventTypes.TRANSACTION_INITIATED,
			EventTypes.TRANSACTION_POSTED,
			EventTypes.TRANSACTION_FAILED,
			EventTypes.HOLD_CREATED,
			EventTypes.HOLD_EXPIRED,
			EventTypes.HOLD_VOIDED
		};

		for (String eventType : allEventTypes) {
			// given
			String payload = "{\"test\":\"data\"}";
			Message<String> message = MessageBuilder
					.withPayload(payload)
					.setHeader(EventTypes.EVENT_TYPE_HEADER, eventType)
					.build();

			// when
			boolean result = EventValidationUtils.validateEventType(message, eventType);

			// then
			assertThat(result).as("Event type %s should be valid", eventType).isTrue();
		}
	}
}
