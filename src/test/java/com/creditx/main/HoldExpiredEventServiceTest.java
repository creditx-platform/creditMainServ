package com.creditx.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.model.Account;
import com.creditx.main.model.AccountType;
import com.creditx.main.model.AccountStatus;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.model.TransactionType;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.service.ProcessedEventService;
import com.creditx.main.service.impl.HoldEventServiceImpl;

@ExtendWith(MockitoExtension.class)
class HoldExpiredEventServiceTest {

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

    private HoldExpiredEvent holdExpiredEvent;
    private Account issuerAccount;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        holdExpiredEvent = HoldExpiredEvent.builder()
                .holdId(123L)
                .transactionId(999L)
                .accountId(1L)
                .amount(new BigDecimal("250.00"))
                .status("EXPIRED")
                .expiresAt(Instant.now().minusSeconds(3600)) // Expired 1 hour ago
                .build();

        issuerAccount = Account.builder()
                .accountId(1L)
                .customerId(100L)
                .type(AccountType.ISSUER)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("750.00")) // Was reduced when hold was created
                .reserved(new BigDecimal("350.00")) // Amount + existing reserved
                .creditLimit(new BigDecimal("1000.00"))
                .build();

        transaction = Transaction.builder()
                .transactionId(999L)
                .type(TransactionType.INBOUND)
                .status(TransactionStatus.AUTHORIZED) // Was authorized when hold was created
                .accountId(1L)
                .holdId(123L)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .build();
    }

    @Test
    void processHoldExpired_success() {
        // Given: Valid event and existing account/transaction
        given(processedEventService.isEventProcessed(anyString())).willReturn(false);
        given(processedEventService.isPayloadProcessed(anyString())).willReturn(false);
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));

        // When: Processing hold expired event
        holdEventService.processHoldExpired(holdExpiredEvent);

        // Then: Account balances should be updated (funds released)
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
           
        Account updatedAccount = accountCaptor.getValue();
        assertThat(updatedAccount.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("1000.00")); // 750 + 250
        assertThat(updatedAccount.getReserved()).isEqualByComparingTo(new BigDecimal("100.00")); // 350 - 250

        // Verify transaction status is updated to FAILED
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        
        Transaction updatedTransaction = transactionCaptor.getValue();
        assertThat(updatedTransaction.getStatus()).isEqualTo(TransactionStatus.FAILED);

        // Verify transaction.failed event is published
        verify(outboxEventService).saveEvent(eq("transaction.failed"), eq(999L), anyString());
    }

    @Test
    void processHoldExpired_accountNotFound() {
        // Given: Account doesn't exist
        given(processedEventService.isEventProcessed(anyString())).willReturn(false);
        given(processedEventService.isPayloadProcessed(anyString())).willReturn(false);
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));
        given(accountRepository.findById(1L)).willReturn(Optional.empty());

        // When & Then: Should throw exception
        assertThatThrownBy(() -> holdEventService.processHoldExpired(holdExpiredEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account not found: 1");
    }

    @Test
    void processHoldExpired_transactionNotFound() {
        // Given: Transaction doesn't exist
        given(processedEventService.isEventProcessed(anyString())).willReturn(false);
        given(processedEventService.isPayloadProcessed(anyString())).willReturn(false);
        given(transactionRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then: Should throw exception
        assertThatThrownBy(() -> holdEventService.processHoldExpired(holdExpiredEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction not found: 999");
    }

    @Test
    void processHoldExpired_reservedBalanceDefensive() {
        // Given: Account with insufficient reserved balance (edge case)
        given(processedEventService.isEventProcessed(anyString())).willReturn(false);
        given(processedEventService.isPayloadProcessed(anyString())).willReturn(false);
        issuerAccount.setReserved(new BigDecimal("100.00")); // Less than the hold amount
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));

        // When: Processing hold expired event
        holdEventService.processHoldExpired(holdExpiredEvent);

        // Then: Reserved balance should be set to zero (defensive behavior)
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
           
        Account updatedAccount = accountCaptor.getValue();
        assertThat(updatedAccount.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("1000.00")); // 750 + 250
        assertThat(updatedAccount.getReserved()).isEqualByComparingTo(BigDecimal.ZERO); // Set to zero defensively
    }

    @Test
    void processHoldExpired_transactionAlreadyCaptured() {
        // Given: Transaction has already been captured (SUCCESS status)
        given(processedEventService.isEventProcessed(anyString())).willReturn(false);
        given(processedEventService.isPayloadProcessed(anyString())).willReturn(false);
        transaction.setStatus(TransactionStatus.SUCCESS);
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));

        // When: Processing hold expired event
        holdEventService.processHoldExpired(holdExpiredEvent);

        // Then: No fund release should occur since transaction is already captured
        verify(accountRepository, never()).findById(1L);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), eq(999L), anyString());
    }

    @Test
    void processHoldExpired_transactionAlreadyFailed() {
        // Given: Transaction has already failed
        given(processedEventService.isEventProcessed(anyString())).willReturn(false);
        given(processedEventService.isPayloadProcessed(anyString())).willReturn(false);
        transaction.setStatus(TransactionStatus.FAILED);
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));

        // When: Processing hold expired event
        holdEventService.processHoldExpired(holdExpiredEvent);

        // Then: No processing should occur since transaction is already failed
        verify(accountRepository, never()).findById(1L);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), eq(999L), anyString());
    }

    @Test
    void processHoldExpired_transactionPending() {
        // Given: Transaction is still pending (shouldn't happen but defensive)
        given(processedEventService.isEventProcessed(anyString())).willReturn(false);
        given(processedEventService.isPayloadProcessed(anyString())).willReturn(false);
        transaction.setStatus(TransactionStatus.PENDING);
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));

        // When: Processing hold expired event
        holdEventService.processHoldExpired(holdExpiredEvent);

        // Then: No processing should occur since transaction is still pending
        verify(accountRepository, never()).findById(1L);
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), eq(999L), anyString());
    }

    @Test
    void processHoldExpired_eventAlreadyProcessed() {
        // Given: Event has already been processed
        given(processedEventService.isEventProcessed(anyString())).willReturn(true);

        // When: Processing hold expired event
        holdEventService.processHoldExpired(holdExpiredEvent);

        // Then: No processing should occur - verify that methods are NOT called
        verify(accountRepository, never()).findById(1L);
        verify(transactionRepository, never()).findById(999L);
        verify(accountRepository, never()).save(issuerAccount);
        verify(transactionRepository, never()).save(transaction);
        verify(outboxEventService, never()).saveEvent(anyString(), eq(999L), anyString());
    }
}
