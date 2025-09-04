package com.creditx.main.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventIdGeneratorTest {

	@Test
	void shouldGenerateUniqueEventId() {
		// given
		String eventType = "transaction.authorized";
		Long transactionId = 123L;

		// when
		String eventId1 = EventIdGenerator.generateEventId(eventType, transactionId);
		String eventId2 = EventIdGenerator.generateEventId(eventType, transactionId);

		// then
		assertThat(eventId1).isNotNull();
		assertThat(eventId2).isNotNull();
		assertThat(eventId1).isNotEqualTo(eventId2); // Should be unique due to UUID suffix
		assertThat(eventId1).startsWith("transaction.authorized-123-");
		assertThat(eventId2).startsWith("transaction.authorized-123-");
		assertThat(eventId1).hasSize(eventType.length() + 1 + transactionId.toString().length() + 1 + 8); // eventType-transactionId-8charUUID
	}

	@Test
	void shouldGenerateEventIdWithDifferentEventTypes() {
		// given
		Long transactionId = 456L;

		// when
		String eventId1 = EventIdGenerator.generateEventId("transaction.authorized", transactionId);
		String eventId2 = EventIdGenerator.generateEventId("transaction.posted", transactionId);

		// then
		assertThat(eventId1).startsWith("transaction.authorized-456-");
		assertThat(eventId2).startsWith("transaction.posted-456-");
		assertThat(eventId1).isNotEqualTo(eventId2);
	}

	@Test
	void shouldGenerateEventIdWithDifferentTransactionIds() {
		// given
		String eventType = "hold.created";

		// when
		String eventId1 = EventIdGenerator.generateEventId(eventType, 111L);
		String eventId2 = EventIdGenerator.generateEventId(eventType, 222L);

		// then
		assertThat(eventId1).startsWith("hold.created-111-");
		assertThat(eventId2).startsWith("hold.created-222-");
		assertThat(eventId1).isNotEqualTo(eventId2);
	}

	@Test
	void shouldGenerateConsistentPayloadHash() {
		// given
		String payload = "{\"transactionId\":123,\"holdId\":456}";

		// when
		String hash1 = EventIdGenerator.generatePayloadHash(payload);
		String hash2 = EventIdGenerator.generatePayloadHash(payload);

		// then
		assertThat(hash1).isNotNull();
		assertThat(hash2).isNotNull();
		assertThat(hash1).isEqualTo(hash2); // Same payload should produce same hash
		assertThat(hash1).hasSize(64); // SHA-256 produces 64 character hex string
	}

	@Test
	void shouldGenerateDifferentHashesForDifferentPayloads() {
		// given
		String payload1 = "{\"transactionId\":123,\"holdId\":456}";
		String payload2 = "{\"transactionId\":789,\"holdId\":101}";

		// when
		String hash1 = EventIdGenerator.generatePayloadHash(payload1);
		String hash2 = EventIdGenerator.generatePayloadHash(payload2);

		// then
		assertThat(hash1).isNotEqualTo(hash2);
		assertThat(hash1).hasSize(64);
		assertThat(hash2).hasSize(64);
	}

	@Test
	void shouldHandleEmptyPayload() {
		// given
		String emptyPayload = "";

		// when
		String hash = EventIdGenerator.generatePayloadHash(emptyPayload);

		// then
		assertThat(hash).isNotNull();
		assertThat(hash).hasSize(64);
	}

	@Test
	void shouldThrowExceptionForNullPayload() {
		// given
		String nullPayload = null;

		// when & then
		org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> {
			EventIdGenerator.generatePayloadHash(nullPayload);
		});
	}

	@Test
	void shouldGenerateValidHexHash() {
		// given
		String payload = "{\"test\":\"data\"}";

		// when
		String hash = EventIdGenerator.generatePayloadHash(payload);

		// then
		assertThat(hash).matches("^[a-f0-9]{64}$"); // Should be valid hex string
	}
}

