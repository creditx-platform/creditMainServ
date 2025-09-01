package com.creditx.main;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.messaging.HoldEventListener;
import com.creditx.main.service.HoldEventService;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

@ExtendWith(MockitoExtension.class)
class HoldExpiredEventListenerTest {

    @Mock
    private HoldEventService holdEventService;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @InjectMocks
    private HoldEventListener holdEventListener;

    @Test
    void holdExpired_shouldProcessValidEvent() {
        // Given: Valid hold expired event payload
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

        // Create message with headers
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Trace-Id", "test-trace-id");
        headers.put("X-Span-Id", "test-span-id");
        headers.put("eventType", "hold.expired");
        Message<String> message = new GenericMessage<>(payload, new MessageHeaders(headers));

        // Mock Tracer and Span
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name("hold-expired-listener")).thenReturn(span);
        when(span.tag("service", "creditMainServ")).thenReturn(span);
        when(span.tag("event.type", "hold.expired")).thenReturn(span);
        when(span.tag("trace.parent.id", "test-trace-id")).thenReturn(span);

        // When: Event is received
        holdEventListener.holdExpired().accept(message);

        // Then: Hold event service should be called
        verify(holdEventService).processHoldExpired(any(HoldExpiredEvent.class));
        verify(span).start();
        verify(span).end();
    }
}
