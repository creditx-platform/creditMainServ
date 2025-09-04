package com.creditx.main.messaging;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import com.creditx.main.constants.EventTypes;
import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.dto.HoldVoidedEvent;
import com.creditx.main.service.HoldEventService;
import com.creditx.main.util.EventValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.creditx.main.tracing.TransactionSpanTagger;

@ExtendWith(MockitoExtension.class)
class HoldEventListenerTest {
        @Mock
        private HoldEventService holdEventService;

        @Mock
        private ObjectMapper objectMapper;

        @Mock
        private TransactionSpanTagger transactionSpanTagger;

        @InjectMocks
        private HoldEventListener holdEventListener;

        private Consumer<Message<String>> holdCreatedConsumer;
        private Consumer<Message<String>> holdExpiredConsumer;
        private Consumer<Message<String>> holdVoidedConsumer;

        @BeforeEach
        void setup() {
                holdCreatedConsumer = holdEventListener.holdCreated();
                holdExpiredConsumer = holdEventListener.holdExpired();
                holdVoidedConsumer = holdEventListener.holdVoided();
        }

        @Test
        void shouldProcessValidHoldCreatedEvent() throws Exception {
                // given
                String payload = "{\"transactionId\":123,\"holdId\":456}";

                Message<String> message = MessageBuilder
                                .withPayload(payload)
                                .setHeader("eventType", EventTypes.HOLD_CREATED)
                                .build();

                HoldCreatedEvent event = new HoldCreatedEvent();
                event.setTransactionId(123L);
                event.setHoldId(456L);

                try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(EventValidationUtils.class)) {
                        mockedUtils.when(() -> EventValidationUtils.validateEventType(message, EventTypes.HOLD_CREATED))
                                        .thenReturn(true);

                        Mockito.lenient().when(objectMapper.readValue(payload, HoldCreatedEvent.class))
                                        .thenReturn(event);

                        // when
                        holdCreatedConsumer.accept(message);

                        // then
                        verify(holdEventService, times(1)).processHoldCreated(event);
                }
        }

        @ParameterizedTest
        @ValueSource(strings = { EventTypes.TRANSACTION_AUTHORIZED, EventTypes.HOLD_EXPIRED, EventTypes.HOLD_VOIDED,
                        EventTypes.TRANSACTION_FAILED, EventTypes.TRANSACTION_INITIATED,
                        EventTypes.TRANSACTION_POSTED })
        void shouldNotProcessInvalidHoldCreatedEvent(String eventType) throws Exception {
                // given
                String payload = "{\"transactionId\":123,\"holdId\":456}";

                Message<String> message = MessageBuilder
                                .withPayload(payload)
                                .setHeader("eventType", eventType)
                                .build();

                HoldCreatedEvent event = new HoldCreatedEvent();
                event.setTransactionId(123L);
                event.setHoldId(456L);

                try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(EventValidationUtils.class)) {
                        mockedUtils.when(() -> EventValidationUtils.validateEventType(message, EventTypes.HOLD_CREATED))
                                        .thenReturn(false);

                        // when
                        holdCreatedConsumer.accept(message);

                        // then
                        verify(holdEventService, never()).processHoldCreated(event);
                }
        }

        @Test
        void shouldProcessValidHoldExpiredEvent() throws Exception {
                // given
                String payload = "{\"transactionId\":123,\"holdId\":456}";

                Message<String> message = MessageBuilder
                                .withPayload(payload)
                                .setHeader("eventType", EventTypes.HOLD_EXPIRED)
                                .build();

                HoldExpiredEvent event = new HoldExpiredEvent();
                event.setTransactionId(123L);
                event.setHoldId(456L);

                try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(EventValidationUtils.class)) {
                        mockedUtils.when(() -> EventValidationUtils.validateEventType(message, EventTypes.HOLD_EXPIRED))
                                        .thenReturn(true);

                        Mockito.lenient().when(objectMapper.readValue(payload, HoldExpiredEvent.class))
                                        .thenReturn(event);

                        // when
                        holdExpiredConsumer.accept(message);

                        // then
                        verify(holdEventService, times(1)).processHoldExpired(event);
                }
        }

        @ParameterizedTest
        @ValueSource(strings = { EventTypes.TRANSACTION_AUTHORIZED, EventTypes.HOLD_CREATED, EventTypes.HOLD_VOIDED,
                        EventTypes.TRANSACTION_FAILED, EventTypes.TRANSACTION_INITIATED,
                        EventTypes.TRANSACTION_POSTED })
        void shouldNotProcessInvalidHoldExpiredEvent(String eventType) throws Exception {
                // given
                String payload = "{\"transactionId\":123,\"holdId\":456}";

                Message<String> message = MessageBuilder
                                .withPayload(payload)
                                .setHeader("eventType", eventType)
                                .build();

                HoldExpiredEvent event = new HoldExpiredEvent();
                event.setTransactionId(123L);
                event.setHoldId(456L);

                try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(EventValidationUtils.class)) {
                        mockedUtils.when(() -> EventValidationUtils.validateEventType(message, EventTypes.HOLD_EXPIRED))
                                        .thenReturn(false);

                        // when
                        holdExpiredConsumer.accept(message);

                        // then
                        verify(holdEventService, never()).processHoldExpired(event);
                }
        }

        @Test
        void shouldProcessValidHoldVoidedEvent() throws Exception {
                // given
                String payload = "{\"transactionId\":123,\"holdId\":456}";

                Message<String> message = MessageBuilder
                                .withPayload(payload)
                                .setHeader("eventType", EventTypes.HOLD_VOIDED)
                                .build();

                HoldVoidedEvent event = new HoldVoidedEvent();
                event.setTransactionId(123L);
                event.setHoldId(456L);

                try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(EventValidationUtils.class)) {
                        mockedUtils.when(() -> EventValidationUtils.validateEventType(message, EventTypes.HOLD_VOIDED))
                                        .thenReturn(true);

                        Mockito.lenient().when(objectMapper.readValue(payload, HoldVoidedEvent.class))
                                        .thenReturn(event);

                        // when
                        holdVoidedConsumer.accept(message);

                        // then
                        verify(holdEventService, times(1)).processHoldVoided(event);
                }
        }

        @ParameterizedTest
        @ValueSource(strings = { EventTypes.TRANSACTION_AUTHORIZED, EventTypes.HOLD_CREATED, EventTypes.HOLD_EXPIRED,
                        EventTypes.TRANSACTION_FAILED, EventTypes.TRANSACTION_INITIATED,
                        EventTypes.TRANSACTION_POSTED })
        void shouldNotProcessInvalidHoldVoidedEvent(String eventType) throws Exception {
                // given
                String payload = "{\"transactionId\":123,\"holdId\":456}";

                Message<String> message = MessageBuilder
                                .withPayload(payload)
                                .setHeader("eventType", eventType)
                                .build();

                HoldVoidedEvent event = new HoldVoidedEvent();
                event.setTransactionId(123L);
                event.setHoldId(456L);

                try (MockedStatic<EventValidationUtils> mockedUtils = Mockito.mockStatic(EventValidationUtils.class)) {
                        mockedUtils.when(() -> EventValidationUtils.validateEventType(message, EventTypes.HOLD_VOIDED))
                                        .thenReturn(false);

                        // when
                        holdVoidedConsumer.accept(message);

                        // then
                        verify(holdEventService, never()).processHoldVoided(event);
                }
        }
}