package com.creditx.main.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventIdGenerator {
    
    /**
     * Generate a unique event ID based on event type and transaction ID
     * @param eventType the type of event
     * @param transactionId the transaction ID
     * @return unique event ID
     */
    public static String generateEventId(String eventType, Long transactionId) {
        return String.format("%s-%d-%s", eventType, transactionId, UUID.randomUUID().toString().substring(0, 8));
    }
    
    /**
     * Generate a hash of the event payload for deduplication
     * @param payload the event payload as string
     * @return SHA-256 hash of the payload
     */
    public static String generatePayloadHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash
            return String.valueOf(payload.hashCode());
        }
    }
}
