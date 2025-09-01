package com.creditx.main.messaging;

import com.creditx.main.constants.EventTypes;
import com.creditx.main.service.HoldEventService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldEventListenerEventTypeValidationTest {

    @Mock
    private HoldEventService holdEventService;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    private HoldEventListener holdEventListener;

    @BeforeEach
    void setUp() {
        holdEventListener = new HoldEventListener(holdEventService, tracer);
        
        // Setup default span behavior
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
    }

    @Test
    void holdCreated_shouldSkipProcessingWhenEventTypeHeaderMissing() {
        // Given: Hold created event without event type header
        String payload = """
                {
                    "holdId": 123,
                    "transactionId": 999,
                    "issuerAccountId": 1,
                    "merchantAccountId": 2,
                    "amount": 250.00,
                    "currency": "USD",
                    "status": "AUTHORIZED",
                    "expiresAt": "2024-01-01T12:00:00Z"
                }
                """;

        // Create message without event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // When: Event is consumed
        holdEventListener.holdCreated().accept(message);

        // Then: Service should not be called and span should not be created
        verify(holdEventService, never()).processHoldCreated(any());
        verify(tracer, never()).nextSpan();
    }

    @Test
    void holdCreated_shouldSkipProcessingWhenEventTypeMismatch() {
        // Given: Hold created event with wrong event type
        String payload = """
                {
                    "holdId": 123,
                    "transactionId": 999,
                    "issuerAccountId": 1,
                    "merchantAccountId": 2,
                    "amount": 250.00,
                    "currency": "USD",
                    "status": "AUTHORIZED",
                    "expiresAt": "2024-01-01T12:00:00Z"
                }
                """;

        // Create message with wrong event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        headers.put(EventTypes.EVENT_TYPE_HEADER, "transaction.authorized"); // Wrong event type
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // When: Event is consumed
        holdEventListener.holdCreated().accept(message);

        // Then: Service should not be called and span should not be created
        verify(holdEventService, never()).processHoldCreated(any());
        verify(tracer, never()).nextSpan();
    }

    @Test
    void holdExpired_shouldSkipProcessingWhenEventTypeHeaderMissing() {
        // Given: Hold expired event without event type header
        String payload = """
                {
                    "holdId": 123,
                    "transactionId": 999,
                    "accountId": 1,
                    "amount": 250.00,
                    "status": "EXPIRED",
                    "expiresAt": "2024-01-01T12:00:00Z"
                }
                """;

        // Create message without event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // When: Event is consumed
        holdEventListener.holdExpired().accept(message);

        // Then: Service should not be called and span should not be created
        verify(holdEventService, never()).processHoldExpired(any());
        verify(tracer, never()).nextSpan();
    }

    @Test
    void holdExpired_shouldSkipProcessingWhenEventTypeMismatch() {
        // Given: Hold expired event with wrong event type
        String payload = """
                {
                    "holdId": 123,
                    "transactionId": 999,
                    "accountId": 1,
                    "amount": 250.00,
                    "status": "EXPIRED",
                    "expiresAt": "2024-01-01T12:00:00Z"
                }
                """;

        // Create message with wrong event type header
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        headers.put(EventTypes.EVENT_TYPE_HEADER, "hold.created"); // Wrong event type
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // When: Event is consumed
        holdEventListener.holdExpired().accept(message);

        // Then: Service should not be called and span should not be created
        verify(holdEventService, never()).processHoldExpired(any());
        verify(tracer, never()).nextSpan();
    }
}
