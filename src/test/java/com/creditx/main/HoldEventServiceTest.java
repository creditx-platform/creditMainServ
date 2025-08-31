package com.creditx.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.model.Account;
import com.creditx.main.model.AccountStatus;
import com.creditx.main.model.AccountType;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.model.TransactionType;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.service.impl.HoldEventServiceImpl;

@ExtendWith(MockitoExtension.class)
class HoldEventServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private HoldEventServiceImpl holdEventService;

    private HoldCreatedEvent holdCreatedEvent;
    private Account issuerAccount;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        holdCreatedEvent = HoldCreatedEvent.builder()
                .holdId(12345L)
                .transactionId(999L)
                .issuerAccountId(1L)
                .merchantAccountId(2L)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .status("AUTHORIZED")
                .expiresAt(Instant.now().plusSeconds(7 * 24 * 60 * 60))
                .build();

        issuerAccount = Account.builder()
                .accountId(1L)
                .customerId(100L)
                .type(AccountType.ISSUER)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("1000.00"))
                .reserved(new BigDecimal("100.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .build();

        transaction = Transaction.builder()
                .transactionId(999L)
                .type(TransactionType.INBOUND)
                .status(TransactionStatus.PENDING)
                .accountId(1L)
                .holdId(12345L)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .build();
    }

    @Test
    void processHoldCreated_success() {
        // Given: Valid event and existing account/transaction
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));

        // When: Processing hold created event
        holdEventService.processHoldCreated(holdCreatedEvent);

        // Then: Account balances should be updated
        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        
        Account updatedAccount = accountCaptor.getValue();
        assertThat(updatedAccount.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("750.00")); // 1000 - 250
        assertThat(updatedAccount.getReserved()).isEqualByComparingTo(new BigDecimal("350.00")); // 100 + 250

        // Verify transaction status is updated
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        
        Transaction updatedTransaction = transactionCaptor.getValue();
        assertThat(updatedTransaction.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);

        // Verify transaction.authorized event is published
        verify(outboxEventService).saveEvent(eq("transaction.authorized"), eq(999L), anyString());
    }

    @Test
    void processHoldCreated_accountNotFound() {
        // Given: Account doesn't exist
        given(accountRepository.findById(1L)).willReturn(Optional.empty());

        // When & Then: Should throw exception
        assertThatThrownBy(() -> holdEventService.processHoldCreated(holdCreatedEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account not found: 1");
    }

    @Test
    void processHoldCreated_transactionNotFound() {
        // Given: Transaction doesn't exist
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(transactionRepository.findById(999L)).willReturn(Optional.empty());

        // When & Then: Should throw exception
        assertThatThrownBy(() -> holdEventService.processHoldCreated(holdCreatedEvent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction not found: 999");
    }

    @Test
    void processHoldCreated_insufficientBalance() {
        // Given: Account with insufficient balance
        Account insufficientAccount = Account.builder()
                .accountId(1L)
                .customerId(100L)
                .type(AccountType.ISSUER)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("100.00")) // Less than hold amount
                .reserved(new BigDecimal("0.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(insufficientAccount));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> holdEventService.processHoldCreated(holdCreatedEvent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Insufficient available balance for hold");
    }

    @Test
    void processHoldCreated_verifyEventPayload() {
        // Given: Valid setup
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(transactionRepository.findById(999L)).willReturn(Optional.of(transaction));

        // When: Processing hold created event
        holdEventService.processHoldCreated(holdCreatedEvent);

        // Then: Verify event payload contains correct structure
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventService).saveEvent(
                eq("transaction.authorized"), 
                eq(999L), 
                payloadCaptor.capture()
        );

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"transactionId\":999");
        assertThat(payload).contains("\"holdId\":12345");
        assertThat(payload).contains("\"issuerAccountId\":1");
        assertThat(payload).contains("\"merchantAccountId\":2");
        assertThat(payload).contains("\"amount\":250.00");
        assertThat(payload).contains("\"currency\":\"USD\"");
        assertThat(payload).contains("\"status\":\"AUTHORIZED\"");
    }
}
