package com.creditx.main.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.creditx.main.dto.CreateTransactionRequest;
import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.model.Account;
import com.creditx.main.model.AccountStatus;
import com.creditx.main.model.AccountType;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.model.TransactionType;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.service.TransactionService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public CreateTransactionResponse createInboundTransaction(CreateTransactionRequest request) {
        // Fetch accounts
        Account issuer = accountRepository.findById(request.getIssuerAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Issuer account not found"));
        Account merchant = accountRepository.findById(request.getMerchantAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Merchant account not found"));

        validateAccounts(issuer, merchant);

        // Persist transaction (accountId references issuer for initial record)
        Transaction txn = Transaction.builder()
                .type(TransactionType.INBOUND)
                .status(TransactionStatus.PENDING)
                .accountId(issuer.getAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .build();
        txn = transactionRepository.save(txn);

        // Outbox event payload
        recordInitiatedEvent(txn, issuer, merchant, request.getAmount(), request.getCurrency());

        return CreateTransactionResponse.builder()
                .transactionId(txn.getTransactionId())
                .status(txn.getStatus())
                .build();
    }

    private void validateAccounts(Account issuer, Account merchant) {
        if (!AccountType.ISSUER.equals(issuer.getType())) {
            throw new IllegalArgumentException("Issuer account type invalid");
        }
        if (!AccountType.MERCHANT.equals(merchant.getType())) {
            throw new IllegalArgumentException("Merchant account type invalid");
        }
        if (!AccountStatus.ACTIVE.equals(issuer.getStatus()) || !AccountStatus.ACTIVE.equals(merchant.getStatus())) {
            throw new IllegalArgumentException("Accounts must be ACTIVE");
        }
    }

    private void recordInitiatedEvent(Transaction txn, Account issuer, Account merchant, BigDecimal amount, String currency) {
        var payload = new InitiatedPayload(txn.getTransactionId(), issuer.getAccountId(), merchant.getAccountId(), amount, currency);
        try {
            outboxEventService.saveEvent("transaction.initiated", txn.getTransactionId(), objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    // Simple record for JSON serialization
    private record InitiatedPayload(Long transactionId, Long issuerAccountId, Long merchantAccountId, BigDecimal amount, String currency) {}
}
