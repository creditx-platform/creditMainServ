package com.creditx.main.service.impl;

import com.creditx.main.dto.CommitTransactionRequest;
import com.creditx.main.dto.CommitTransactionResponse;
import com.creditx.main.dto.CreateCashbackTransactionRequest;
import com.creditx.main.dto.CreateHoldRequest;
import com.creditx.main.dto.CreateHoldResponse;
import com.creditx.main.dto.CreateTransactionRequest;
import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.model.Account;
import com.creditx.main.model.AccountStatus;
import com.creditx.main.model.AccountType;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionEntry;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.model.TransactionType;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionEntryRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.service.TransactionService;
import com.creditx.main.tracing.TransactionSpanTagger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {

  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final OutboxEventService outboxEventService;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TransactionEntryRepository transactionEntryRepository;
  private final TransactionSpanTagger transactionSpanTagger;
  @Value("${app.credithold.url:http://localhost:8081}")
  private String creditHoldServiceUrl;

  @PostConstruct
  public void configureObjectMapper() {
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Override
  @Transactional
  public CreateTransactionResponse createInboundTransaction(CreateTransactionRequest request) {
    log.info("Creating inbound transaction for issuer: {}, merchant: {}, amount: {}",
        request.getIssuerAccountId(), request.getMerchantAccountId(), request.getAmount());

    // Fetch accounts
    Account issuer = accountRepository.findById(request.getIssuerAccountId())
        .orElseThrow(() -> new IllegalArgumentException("Issuer account not found"));
    Account merchant = accountRepository.findById(request.getMerchantAccountId())
        .orElseThrow(() -> new IllegalArgumentException("Merchant account not found"));

    log.info("Found accounts - Issuer: {}, Merchant: {}", issuer.getAccountId(),
        merchant.getAccountId());
    validateAccounts(issuer, merchant, request.getAmount());

    // Create Transaction with status = PENDING
    Transaction txn = Transaction.builder().type(TransactionType.INBOUND)
        .status(TransactionStatus.PENDING).accountId(issuer.getAccountId())
        .merchantId(merchant.getAccountId()).amount(request.getAmount())
        .currency(request.getCurrency()).build();
    txn = transactionRepository.save(txn);
    // Tag the current span with the new transactionId for trace correlation
    transactionSpanTagger.tagTransactionId(txn.getTransactionId());

    // Outbox event payload
    recordInitiatedEvent(txn, issuer, merchant, request.getAmount(), request.getCurrency());

    // Send hold request to CreditHoldServ
    CreateHoldResponse holdResponse = sendHoldRequest(txn, issuer, merchant, request.getAmount(),
        request.getCurrency());

    // Update transaction with hold_id and status
    txn.setHoldId(holdResponse.getHoldId());
    log.info("=== TRANSACTION STATUS UPDATE START ===");
    log.info("Hold response status: {}", holdResponse.getStatus());
    log.info("Current transaction status before update: {}", txn.getStatus());

    // Update transaction status based on hold response
    if ("AUTHORIZED".equals(holdResponse.getStatus().toString())) {
      log.info("Hold status is AUTHORIZED, updating transaction status to AUTHORIZED");
      txn.setStatus(TransactionStatus.AUTHORIZED);
    } else {
      log.info("Hold status is NOT AUTHORIZED: {}", holdResponse.getStatus());
      txn.setStatus(TransactionStatus.FAILED);
    }
    log.info("Transaction status after update: {}", txn.getStatus());
    transactionRepository.save(txn);
    log.info("=== TRANSACTION STATUS UPDATE END ===");

    // Response
    return CreateTransactionResponse.builder().transactionId(txn.getTransactionId())
        .status(txn.getStatus()).build();
  }

  @Override
  @Transactional
  public CreateTransactionResponse createCashbackTransaction(
      CreateCashbackTransactionRequest request) {
    // Fetch accounts (issuer credited, merchant debited)
    Account issuer = accountRepository.findById(request.getIssuerAccountId())
        .orElseThrow(() -> new IllegalArgumentException("Issuer account not found"));
    Account merchant = accountRepository.findById(request.getMerchantAccountId())
        .orElseThrow(() -> new IllegalArgumentException("Merchant account not found"));

    // Basic validation (skip hold flow for cashback)
    if (!AccountStatus.ACTIVE.equals(issuer.getStatus()) || !AccountStatus.ACTIVE.equals(
        merchant.getStatus())) {
      throw new IllegalArgumentException("Accounts must be ACTIVE");
    }
    if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Amount must be positive");
    }

    Transaction txn = Transaction.builder().type(TransactionType.CASHBACK)
        .status(TransactionStatus.SUCCESS).accountId(issuer.getAccountId())
        .merchantId(merchant.getAccountId()).amount(request.getAmount())
        .currency(request.getCurrency()).build();
    txn = transactionRepository.save(txn);
    transactionSpanTagger.tagTransactionId(txn.getTransactionId());

    // Post double-entry (credit issuer, debit merchant)
    issuer.setAvailableBalance(issuer.getAvailableBalance().add(request.getAmount()));
    merchant.setAvailableBalance(merchant.getAvailableBalance().subtract(request.getAmount()));
    accountRepository.save(issuer);
    accountRepository.save(merchant);

    // Entries
    TransactionEntry merchantEntry = TransactionEntry.builder().transaction(txn)
        .accountId(merchant.getAccountId()).amount(request.getAmount().negate()).build();
    TransactionEntry issuerEntry = TransactionEntry.builder().transaction(txn)
        .accountId(issuer.getAccountId()).amount(request.getAmount()).build();
    transactionEntryRepository.save(merchantEntry);
    transactionEntryRepository.save(issuerEntry);

    // Outbox event (transaction.posted)
    recordPostedEvent(txn, issuer, merchant);

    return CreateTransactionResponse.builder().transactionId(txn.getTransactionId())
        .status(txn.getStatus()).build();
  }

  @Override
  @Transactional
  public CommitTransactionResponse commitTransaction(Long transactionId,
      CommitTransactionRequest request) {
    // Find transaction by ID and holdId for idempotency
    Transaction transaction = transactionRepository.findById(transactionId)
        .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
    // Ensure span (if present) is tagged even on follow-up operations
    transactionSpanTagger.tagTransactionId(transaction.getTransactionId());

    // Validate transaction is in AUTHORIZED state
    if (!TransactionStatus.AUTHORIZED.equals(transaction.getStatus())) {
      throw new IllegalArgumentException("Transaction must be in AUTHORIZED state to commit");
    }

    // Validate holdId matches
    if (!request.getHoldId().equals(transaction.getHoldId())) {
      throw new IllegalArgumentException("Hold ID mismatch");
    }

    // Find accounts
    Account issuer = accountRepository.findById(transaction.getAccountId())
        .orElseThrow(() -> new IllegalArgumentException("Issuer account not found"));

    // Find merchant account from transaction or use the merchantId field
    Account merchant = accountRepository.findById(transaction.getMerchantId())
        .orElseThrow(() -> new IllegalArgumentException("Merchant account not found"));

    // Perform double-entry posting
    performDoubleEntryPosting(transaction, issuer, merchant);

    // Update transaction status to SUCCESS
    transaction.setStatus(TransactionStatus.SUCCESS);
    transactionRepository.save(transaction);

    // Record outbox event for transaction.posted
    recordPostedEvent(transaction, issuer, merchant);

    return CommitTransactionResponse.builder().transactionId(transaction.getTransactionId())
        .status(TransactionStatus.SUCCESS).message("Transaction committed successfully").build();
  }

  private void validateAccounts(Account issuer, Account merchant, BigDecimal amount) {
    if (!AccountType.ISSUER.equals(issuer.getType())) {
      throw new IllegalArgumentException("Issuer account type invalid");
    }
    if (!AccountType.MERCHANT.equals(merchant.getType())) {
      throw new IllegalArgumentException("Merchant account type invalid");
    }
    if (!AccountStatus.ACTIVE.equals(issuer.getStatus()) || !AccountStatus.ACTIVE.equals(
        merchant.getStatus())) {
      throw new IllegalArgumentException("Accounts must be ACTIVE");
    }

    // Validate sufficient available balance
    if (issuer.getAvailableBalance().compareTo(amount) < 0) {
      throw new IllegalArgumentException("Insufficient available balance");
    }
  }

  private void recordInitiatedEvent(Transaction txn, Account issuer, Account merchant,
      BigDecimal amount, String currency) {
    var payload = new InitiatedPayload(txn.getTransactionId(), issuer.getAccountId(),
        merchant.getAccountId(), amount, currency);
    try {
      outboxEventService.saveEvent("transaction.initiated", txn.getTransactionId(),
          objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize event payload", e);
    }
  }

  private CreateHoldResponse sendHoldRequest(Transaction txn, Account issuer, Account merchant,
      BigDecimal amount, String currency) {
    log.info("=== SEND HOLD REQUEST START ===");
    log.info("CreditHoldServiceUrl configured as: {}", creditHoldServiceUrl);
    log.info("Sending hold request for transaction {}", txn.getTransactionId());

    CreateHoldRequest holdRequest = CreateHoldRequest.builder()
        .transactionId(txn.getTransactionId()).issuerAccountId(issuer.getAccountId())
        .merchantAccountId(merchant.getAccountId()).amount(amount).currency(currency).build();

    log.info(
        "Hold request payload: transactionId={}, issuerAccountId={}, merchantAccountId={}, amount={}, currency={}",
        holdRequest.getTransactionId(), holdRequest.getIssuerAccountId(),
        holdRequest.getMerchantAccountId(), holdRequest.getAmount(), holdRequest.getCurrency());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<CreateHoldRequest> entity = new HttpEntity<>(holdRequest, headers);

    try {
      String url = creditHoldServiceUrl + "/api/holds";
      log.info("Making POST request to URL: {}", url);
      var response = restTemplate.postForEntity(url, entity, CreateHoldResponse.class);
      log.info("Response status: {}", response.getStatusCode());
      log.info("Response body: {}", response.getBody());

      CreateHoldResponse holdResponse = response.getBody();
      if (holdResponse != null && holdResponse.getHoldId() != null) {
        log.info("Hold created successfully with ID: {}", holdResponse.getHoldId());
        log.info("=== SEND HOLD REQUEST SUCCESS ===");
        return holdResponse;
      }
      log.error("Invalid hold response from CreditHoldServ: {}", holdResponse);
      throw new RuntimeException("Invalid hold response from CreditHoldServ");
    } catch (Exception e) {
      log.error("Exception during hold request: {}", e.getMessage());
      log.error("Exception type: {}", e.getClass().getSimpleName());
      log.error("=== SEND HOLD REQUEST FAILED ===", e);
      throw new RuntimeException("Failed to send hold request to CreditHoldServ", e);
    }
  }

  private void performDoubleEntryPosting(Transaction transaction, Account issuer,
      Account merchant) {
    BigDecimal amount = transaction.getAmount();

    // Validate transaction amount is positive
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalStateException("Transaction amount must be positive: " + amount);
    }

    // Debit issuer account (decrease available balance)
    issuer.setAvailableBalance(issuer.getAvailableBalance().subtract(amount));

    // Credit merchant account (increase available balance)
    merchant.setAvailableBalance(merchant.getAvailableBalance().add(amount));

    // Release the hold (decrease reserved amount)
    issuer.setReserved(issuer.getReserved().subtract(amount));

    // Save account changes
    accountRepository.save(issuer);
    accountRepository.save(merchant);

    // Create transaction entries for audit trail
    createTransactionEntries(transaction, issuer, merchant, amount);
  }

  private void createTransactionEntries(Transaction transaction, Account issuer, Account merchant,
      BigDecimal amount) {
    // Debit entry for issuer
    TransactionEntry issuerEntry = TransactionEntry.builder().transaction(transaction)
        .accountId(issuer.getAccountId()).amount(amount.negate()) // Negative for debit
        .build();

    // Credit entry for merchant
    TransactionEntry merchantEntry = TransactionEntry.builder().transaction(transaction)
        .accountId(merchant.getAccountId()).amount(amount) // Positive for credit
        .build();

    // Save entries
    transactionEntryRepository.save(issuerEntry);
    transactionEntryRepository.save(merchantEntry);
  }

  private void recordPostedEvent(Transaction transaction, Account issuer, Account merchant) {
    var payload = new PostedPayload(transaction.getTransactionId(), transaction.getType(),
        issuer.getAccountId(), merchant.getAccountId(), transaction.getAmount(),
        transaction.getCurrency(), transaction.getCreatedAt());
    try {
      outboxEventService.saveEvent("transaction.posted", transaction.getTransactionId(),
          objectMapper.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize event payload", e);
    }
  }

  // Simple record for JSON serialization
  private record InitiatedPayload(Long transactionId, Long issuerAccountId, Long merchantAccountId,
                                  BigDecimal amount, String currency) {

  }

  private record PostedPayload(Long transactionId, TransactionType type, Long issuerAccountId,
                               Long merchantAccountId, BigDecimal amount, String currency,
                               Instant createdAt) {

  }
}
