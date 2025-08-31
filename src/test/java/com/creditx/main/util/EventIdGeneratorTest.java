package com.creditx.main.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventIdGeneratorTest {

    @Test
    void generateEventId_createsUniqueEventId() {
        // Given
        String eventType = "hold.created";
        Long transactionId = 12345L;

        // When
        String eventId1 = EventIdGenerator.generateEventId(eventType, transactionId);
        String eventId2 = EventIdGenerator.generateEventId(eventType, transactionId);

        // Then
        assertThat(eventId1).startsWith("hold.created-12345-");
        assertThat(eventId1).hasSize(27); // eventType + "-" + transactionId + "-" + 8 chars
        assertThat(eventId1).isNotEqualTo(eventId2); // Should be unique due to UUID
    }

    @Test
    void generatePayloadHash_createsConsistentHash() {
        // Given
        String payload = "{\"transactionId\":12345,\"amount\":100.00}";

        // When
        String hash1 = EventIdGenerator.generatePayloadHash(payload);
        String hash2 = EventIdGenerator.generatePayloadHash(payload);

        // Then
        assertThat(hash1).isEqualTo(hash2); // Same payload should produce same hash
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 character hex string
        assertThat(hash1).matches("^[a-f0-9]+$"); // Should be hex string
    }

    @Test
    void generatePayloadHash_createsDifferentHashesForDifferentPayloads() {
        // Given
        String payload1 = "{\"transactionId\":12345,\"amount\":100.00}";
        String payload2 = "{\"transactionId\":12345,\"amount\":200.00}";

        // When
        String hash1 = EventIdGenerator.generatePayloadHash(payload1);
        String hash2 = EventIdGenerator.generatePayloadHash(payload2);

        // Then
        assertThat(hash1).isNotEqualTo(hash2); // Different payloads should produce different hashes
    }

    @Test
    void generateEventId_handlesNullValues() {
        // Given
        String eventType = null;
        Long transactionId = null;

        // When
        String eventId = EventIdGenerator.generateEventId(eventType, transactionId);

        // Then
        assertThat(eventId).startsWith("null-null-");
        assertThat(eventId).hasSize(18); // "null-null-" + 8 chars
    }
}
