package com.creditx.main.util;

import com.creditx.main.constants.EventTypes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;

/**
 * Utility class for validating event messages and their types.
 */
@Slf4j
public final class EventValidationUtils {
    
    private EventValidationUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Validates that the message contains the expected event type in its headers.
     * 
     * @param message the message to validate
     * @param expectedEventType the expected event type
     * @return true if the event type matches, false otherwise
     */
    public static boolean validateEventType(Message<String> message, String expectedEventType) {
        if (message == null) {
            log.warn("Message is null, cannot validate event type");
            return false;
        }
        
        if (expectedEventType == null || expectedEventType.trim().isEmpty()) {
            log.warn("Expected event type is null or empty");
            return false;
        }
        
        Object eventTypeHeader = message.getHeaders().get(EventTypes.EVENT_TYPE_HEADER);
        
        if (eventTypeHeader == null) {
            log.warn("Message does not contain {} header. Message headers: {}", 
                    EventTypes.EVENT_TYPE_HEADER, message.getHeaders().keySet());
            return false;
        }
        
        String actualEventType = eventTypeHeader.toString();
        
        if (!expectedEventType.equals(actualEventType)) {
            log.warn("Event type mismatch. Expected: {}, Actual: {}, Payload: {}", 
                    expectedEventType, actualEventType, message.getPayload());
            return false;
        }
        
        log.debug("Event type validation passed. Event type: {}", actualEventType);
        return true;
    }
    
    /**
     * Gets the event type from the message headers.
     * 
     * @param message the message to extract event type from
     * @return the event type or null if not present
     */
    public static String getEventType(Message<String> message) {
        if (message == null) {
            return null;
        }
        
        Object eventTypeHeader = message.getHeaders().get(EventTypes.EVENT_TYPE_HEADER);
        return eventTypeHeader != null ? eventTypeHeader.toString() : null;
    }
}
