package com.creditx.main;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.messaging.HoldEventListener;
import com.creditx.main.service.HoldEventService;

@ExtendWith(MockitoExtension.class)
class HoldExpiredEventListenerTest {

    @Mock
    private HoldEventService holdEventService;

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

        // When: Event is received
        holdEventListener.holdExpired().accept(payload);

        // Then: Hold event service should be called
        verify(holdEventService).processHoldExpired(any(HoldExpiredEvent.class));
    }
}
