package com.creditx.main.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.dto.HoldVoidedEvent;
import com.creditx.main.model.Account;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.service.ProcessedEventService;
import com.creditx.main.util.EventIdGenerator;

@ExtendWith(MockitoExtension.class)
class HoldEventServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private ProcessedEventService processedEventService;

    @InjectMocks
    private HoldEventServiceImpl holdEventService;

    @BeforeEach
    void setup() {
    }

    @Test
    void shouldProcessHoldCreatedEvent() {
        // given
        HoldCreatedEvent event = createHoldCreatedEvent();
        String eventId = "hold.created-123";
        String payloadHash = "hash123";
        Account account = createAccount();
        Transaction transaction = createTransaction();

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("hold.created", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(false);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(transactionRepository.findById(123L)).thenReturn(Optional.of(transaction));

            // when
            holdEventService.processHoldCreated(event);

            // then
            verify(processedEventService, times(1)).isEventProcessed(eventId);
            verify(processedEventService, times(1)).isPayloadProcessed(payloadHash);
            verify(accountRepository, times(1)).findById(1L);
            verify(accountRepository, times(1)).save(account);
            verify(transactionRepository, times(1)).findById(123L);
            verify(transactionRepository, times(1)).save(transaction);
            verify(outboxEventService, times(1)).saveEvent(anyString(), eq(123L), anyString());
            verify(processedEventService, times(1)).markEventAsProcessed(eventId, payloadHash, "SUCCESS");

            assertThat(account.getAvailableBalance()).isEqualTo(new BigDecimal("900.00"));
            assertThat(account.getReserved()).isEqualTo(new BigDecimal("200.00")); // 100.00 initial + 100.00 hold amount
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);
        }
    }

    @Test
    void shouldSkipProcessingWhenEventAlreadyProcessed() {
        // given
        HoldCreatedEvent event = createHoldCreatedEvent();
        String eventId = "hold.created-123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("hold.created", 123L))
                    .thenReturn(eventId);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(true);

            // when
            holdEventService.processHoldCreated(event);

            // then
            verify(processedEventService, times(1)).isEventProcessed(eventId);
            verify(processedEventService, never()).isPayloadProcessed(anyString());
            verify(accountRepository, never()).findById(any());
            verify(transactionRepository, never()).findById(any());
            verify(processedEventService, never()).markEventAsProcessed(anyString(), anyString(), anyString());
        }
    }

    @Test
    void shouldSkipProcessingWhenPayloadAlreadyProcessed() {
        // given
        HoldCreatedEvent event = createHoldCreatedEvent();
        String eventId = "hold.created-123";
        String payloadHash = "hash123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("hold.created", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(true);

            // when
            holdEventService.processHoldCreated(event);

            // then
            verify(processedEventService, times(1)).isEventProcessed(eventId);
            verify(processedEventService, times(1)).isPayloadProcessed(payloadHash);
            verify(accountRepository, never()).findById(any());
            verify(transactionRepository, never()).findById(any());
            verify(processedEventService, never()).markEventAsProcessed(anyString(), anyString(), anyString());
        }
    }

    @Test
    void shouldThrowExceptionWhenAccountNotFound() {
        // given
        HoldCreatedEvent event = createHoldCreatedEvent();
        String eventId = "hold.created-123";
        String payloadHash = "hash123";

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("hold.created", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(false);
            when(accountRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> holdEventService.processHoldCreated(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Account not found: 1");

            verify(processedEventService, times(1)).markEventAsProcessed(eventId, "", "FAILED");
        }
    }

    @Test
    void shouldProcessHoldExpiredEvent() {
        // given
        HoldExpiredEvent event = createHoldExpiredEvent();
        String eventId = "hold.expired-123";
        String payloadHash = "hash123";
        Account account = createAccount();
        Transaction transaction = createAuthorizedTransaction();

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("hold.expired", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(false);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(transactionRepository.findById(123L)).thenReturn(Optional.of(transaction));

            // when
            holdEventService.processHoldExpired(event);

            // then
            verify(processedEventService, times(1)).markEventAsProcessed(eventId, payloadHash, "SUCCESS");
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }
    }

    @Test
    void shouldProcessHoldVoidedEvent() {
        // given
        HoldVoidedEvent event = createHoldVoidedEvent();
        String eventId = "hold.voided-123";
        String payloadHash = "hash123";
        Account account = createAccount();
        Transaction transaction = createAuthorizedTransaction();

        try (MockedStatic<EventIdGenerator> mockedGenerator = Mockito.mockStatic(EventIdGenerator.class)) {
            mockedGenerator.when(() -> EventIdGenerator.generateEventId("hold.voided", 123L))
                    .thenReturn(eventId);
            mockedGenerator.when(() -> EventIdGenerator.generatePayloadHash(anyString()))
                    .thenReturn(payloadHash);

            when(processedEventService.isEventProcessed(eventId)).thenReturn(false);
            when(processedEventService.isPayloadProcessed(payloadHash)).thenReturn(false);
            when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
            when(transactionRepository.findById(123L)).thenReturn(Optional.of(transaction));

            // when
            holdEventService.processHoldVoided(event);

            // then
            verify(processedEventService, times(1)).markEventAsProcessed(eventId, payloadHash, "SUCCESS");
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        }
    }

    private HoldCreatedEvent createHoldCreatedEvent() {
        HoldCreatedEvent event = new HoldCreatedEvent();
        event.setTransactionId(123L);
        event.setHoldId(456L);
        event.setIssuerAccountId(1L);
        event.setMerchantAccountId(2L);
        event.setAmount(new BigDecimal("100.00"));
        return event;
    }

    private HoldExpiredEvent createHoldExpiredEvent() {
        HoldExpiredEvent event = new HoldExpiredEvent();
        event.setTransactionId(123L);
        event.setHoldId(456L);
        event.setAccountId(1L);
        event.setAmount(new BigDecimal("100.00"));
        return event;
    }

    private HoldVoidedEvent createHoldVoidedEvent() {
        HoldVoidedEvent event = new HoldVoidedEvent();
        event.setTransactionId(123L);
        event.setHoldId(456L);
        event.setAccountId(1L);
        event.setAmount(new BigDecimal("100.00"));
        event.setReason("Test void");
        return event;
    }

    private Account createAccount() {
        Account account = new Account();
        account.setAccountId(1L);
        account.setAvailableBalance(new BigDecimal("1000.00"));
        account.setReserved(new BigDecimal("100.00")); // Set some reserved amount to subtract from
        return account;
    }

    private Transaction createTransaction() {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(123L);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setAmount(new BigDecimal("100.00"));
        return transaction;
    }

    private Transaction createAuthorizedTransaction() {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(123L);
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setAmount(new BigDecimal("100.00"));
        return transaction;
    }
}