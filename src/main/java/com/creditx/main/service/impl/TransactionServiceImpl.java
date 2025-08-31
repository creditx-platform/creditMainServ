package com.creditx.main.service.impl;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.creditx.main.dto.CreateHoldRequest;
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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.credithold.url:http://localhost:8081}")
    private String creditHoldServiceUrl;

    @Override
    @Transactional
    public CreateTransactionResponse createInboundTransaction(CreateTransactionRequest request) {
        // Fetch accounts
        Account issuer = accountRepository.findById(request.getIssuerAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Issuer account not found"));
        Account merchant = accountRepository.findById(request.getMerchantAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Merchant account not found"));

        validateAccounts(issuer, merchant, request.getAmount());

        // Create Transaction with status = PENDING
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

        // Send hold request to CreditHoldServ
        sendHoldRequest(txn, issuer, merchant, request.getAmount(), request.getCurrency());

        // Response
        return CreateTransactionResponse.builder()
                .transactionId(txn.getTransactionId())
                .status(txn.getStatus())
                .build();
    }

    private void validateAccounts(Account issuer, Account merchant, BigDecimal amount) {
        if (!AccountType.ISSUER.equals(issuer.getType())) {
            throw new IllegalArgumentException("Issuer account type invalid");
        }
        if (!AccountType.MERCHANT.equals(merchant.getType())) {
            throw new IllegalArgumentException("Merchant account type invalid");
        }
        if (!AccountStatus.ACTIVE.equals(issuer.getStatus()) || !AccountStatus.ACTIVE.equals(merchant.getStatus())) {
            throw new IllegalArgumentException("Accounts must be ACTIVE");
        }
        
        // Validate sufficient available balance
        if (issuer.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient available balance");
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

    private void sendHoldRequest(Transaction txn, Account issuer, Account merchant, BigDecimal amount, String currency) {
        CreateHoldRequest holdRequest = CreateHoldRequest.builder()
                .transactionId(txn.getTransactionId())
                .issuerAccountId(issuer.getAccountId())
                .merchantAccountId(merchant.getAccountId())
                .amount(amount)
                .currency(currency)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CreateHoldRequest> entity = new HttpEntity<>(holdRequest, headers);

        try {
            restTemplate.postForEntity(creditHoldServiceUrl + "/holds", entity, String.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send hold request to CreditHoldServ", e);
        }
    }

    // Simple record for JSON serialization
    private record InitiatedPayload(Long transactionId, Long issuerAccountId, Long merchantAccountId, BigDecimal amount, String currency) {}
}
