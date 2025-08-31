package com.creditx.main.service;

public interface ProcessedEventService {
    
    /**
     * Check if an event has already been processed
     * @param eventId unique identifier for the event
     * @return true if event was already processed, false otherwise
     */
    boolean isEventProcessed(String eventId);
    
    /**
     * Mark an event as processed
     * @param eventId unique identifier for the event
     * @param payloadHash hash of the event payload for additional deduplication
     * @param status status of the processed event
     */
    void markEventAsProcessed(String eventId, String payloadHash, String status);
    
    /**
     * Check if an event with the same payload hash has been processed
     * @param payloadHash hash of the event payload
     * @return true if payload was already processed, false otherwise
     */
    boolean isPayloadProcessed(String payloadHash);
}
