package com.creditx.main.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.model.Account;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.HoldEventService;
import com.creditx.main.service.OutboxEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HoldEventServiceImpl implements HoldEventService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public void processHoldCreated(HoldCreatedEvent event) {
        // Update account balances (atomic with optimistic locking)
        Account account = accountRepository.findById(event.getIssuerAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + event.getIssuerAccountId()));
        
        updateAccountBalances(account, event.getAmount());
        accountRepository.save(account);

        // Update transaction state
        Transaction transaction = transactionRepository.findById(event.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + event.getTransactionId()));
        
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transactionRepository.save(transaction);

        // Publish transaction.authorized event
        publishTransactionAuthorized(transaction, event);
    }

    private void updateAccountBalances(Account account, BigDecimal amount) {
        // Atomic balance update: available_balance -= amount, reserved += amount
        BigDecimal newAvailableBalance = account.getAvailableBalance().subtract(amount);
        BigDecimal newReservedBalance = account.getReserved().add(amount);
        
        // Validate sufficient balance (should already be checked, but defensive)
        if (newAvailableBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Insufficient available balance for hold");
        }
        
        account.setAvailableBalance(newAvailableBalance);
        account.setReserved(newReservedBalance);
    }

    private void publishTransactionAuthorized(Transaction transaction, HoldCreatedEvent holdEvent) {
        var payload = new AuthorizedPayload(
                transaction.getTransactionId(),
                holdEvent.getHoldId(),
                holdEvent.getIssuerAccountId(),
                holdEvent.getMerchantAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().toString()
        );

        try {
            outboxEventService.saveEvent(
                    "transaction.authorized",
                    transaction.getTransactionId(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize transaction authorized event payload", e);
        }
    }

    // Simple record for JSON serialization
    private record AuthorizedPayload(
            Long transactionId,
            Long holdId,
            Long issuerAccountId,
            Long merchantAccountId,
            BigDecimal amount,
            String currency,
            String status
    ) {}
}
