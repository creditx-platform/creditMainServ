package com.creditx.main.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.creditx.main.dto.HoldCreatedEvent;
import com.creditx.main.dto.HoldExpiredEvent;
import com.creditx.main.dto.HoldVoidedEvent;
import com.creditx.main.model.Account;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.HoldEventService;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.service.ProcessedEventService;
import jakarta.validation.constraints.NotNull;
import com.creditx.main.util.EventIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HoldEventServiceImpl implements HoldEventService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final OutboxEventService outboxEventService;
    private final ProcessedEventService processedEventService;
    private final ObjectMapper objectMapper;

    public HoldEventServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository,
                               OutboxEventService outboxEventService, ProcessedEventService processedEventService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.outboxEventService = outboxEventService;
        this.processedEventService = processedEventService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // This enables JSR310 module for Instant serialization
    }

    @Override
    @Transactional
    public void processHoldCreated(HoldCreatedEvent event) {
        // Generate unique event ID for deduplication
        String eventId = EventIdGenerator.generateEventId("hold.created", event.getTransactionId());
        
        // Check if event has already been processed
        if (processedEventService.isEventProcessed(eventId)) {
            log.info("Event {} has already been processed, skipping", eventId);
            return;
        }
        
        try {
            // Generate payload hash for additional deduplication
            String payload = objectMapper.writeValueAsString(event);
            String payloadHash = EventIdGenerator.generatePayloadHash(payload);
            
            // Check if payload has already been processed
            if (processedEventService.isPayloadProcessed(payloadHash)) {
                log.info("Payload with hash {} has already been processed, skipping", payloadHash);
                return;
            }
            
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
            
            // Mark event as processed
            processedEventService.markEventAsProcessed(eventId, payloadHash, "SUCCESS");
            log.info("Successfully processed hold.created event for transaction: {}", event.getTransactionId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for transaction: {}", event.getTransactionId(), e);
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw new RuntimeException("Failed to serialize event payload", e);
        } catch (Exception e) {
            log.error("Failed to process hold.created event for transaction: {}", event.getTransactionId(), e);
            // Mark event as processed with failed status
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw e;
        }
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
        // Validate that holdEvent has a holdId - this should never be null
        if (holdEvent.getHoldId() == null) {
            log.error("Cannot publish transaction.authorized event - holdId is null for transaction: {}", transaction.getTransactionId());
            throw new IllegalStateException("HoldId cannot be null when publishing transaction.authorized event");
        }
        
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
            log.debug("Published transaction.authorized event for transaction: {} with holdId: {}", 
                    transaction.getTransactionId(), holdEvent.getHoldId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize transaction authorized event payload", e);
        }
    }

    @Override
    @Transactional
    public void processHoldExpired(HoldExpiredEvent event) {
        // Generate unique event ID for deduplication
        String eventId = EventIdGenerator.generateEventId("hold.expired", event.getTransactionId());
        
        // Check if event has already been processed
        if (processedEventService.isEventProcessed(eventId)) {
            log.info("Event {} has already been processed, skipping", eventId);
            return;
        }
        
        try {
            // Generate payload hash for additional deduplication
            String payload = objectMapper.writeValueAsString(event);
            String payloadHash = EventIdGenerator.generatePayloadHash(payload);
            
            // Check if payload has already been processed
            if (processedEventService.isPayloadProcessed(payloadHash)) {
                log.info("Payload with hash {} has already been processed, skipping", payloadHash);
                return;
            }
            
            // Find transaction by ID first
            Transaction transaction = transactionRepository.findById(event.getTransactionId())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + event.getTransactionId()));
            
            // Check if transaction is in a state that allows expiry
            if (!isTransactionExpirable(transaction)) {
                log.info("Transaction {} is in status {} and cannot be expired, skipping", 
                        transaction.getTransactionId(), transaction.getStatus());
                processedEventService.markEventAsProcessed(eventId, payloadHash, "SKIPPED");
                return;
            }
            
            // Find account by accountId from the hold expired event
            Account account = accountRepository.findById(event.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + event.getAccountId()));
            
            // Release funds: available_balance += amount, reserved -= amount
            releaseFunds(account, event.getAmount());
            accountRepository.save(account);

            // Update transaction status to FAILED
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);

            // Publish transaction.failed event
            publishTransactionFailed(transaction, event);
            
            // Mark event as processed
            processedEventService.markEventAsProcessed(eventId, payloadHash, "SUCCESS");
            log.info("Successfully processed hold.expired event for transaction: {}", event.getTransactionId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for transaction: {}", event.getTransactionId(), e);
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw new RuntimeException("Failed to serialize event payload", e);
        } catch (Exception e) {
            log.error("Failed to process hold.expired event for transaction: {}", event.getTransactionId(), e);
            // Mark event as processed with failed status
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw e;
        }
    }

    @Override
    @Transactional
    public void processHoldVoided(HoldVoidedEvent event) {
        // Generate unique event ID for deduplication
        String eventId = EventIdGenerator.generateEventId("hold.voided", event.getTransactionId());
        
        // Check if event has already been processed
        if (processedEventService.isEventProcessed(eventId)) {
            log.info("Event {} has already been processed, skipping", eventId);
            return;
        }
        
        try {
            // Generate payload hash for additional deduplication
            String payload = objectMapper.writeValueAsString(event);
            String payloadHash = EventIdGenerator.generatePayloadHash(payload);
            
            // Check if payload has already been processed
            if (processedEventService.isPayloadProcessed(payloadHash)) {
                log.info("Payload with hash {} has already been processed, skipping", payloadHash);
                return;
            }
            
            // Find transaction by ID first
            Transaction transaction = transactionRepository.findById(event.getTransactionId())
                    .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + event.getTransactionId()));
            
            // Check if transaction is in a state that allows voiding (similar to expiry logic)
            if (!isTransactionVoidable(transaction)) {
                log.info("Transaction {} is in status {} and cannot be voided, skipping", 
                        transaction.getTransactionId(), transaction.getStatus());
                processedEventService.markEventAsProcessed(eventId, payloadHash, "SKIPPED");
                return;
            }
            
            // Find account by accountId from the hold voided event
            Account account = accountRepository.findById(event.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + event.getAccountId()));
            
            // Release funds: available_balance += amount, reserved -= amount
            releaseFunds(account, event.getAmount());
            accountRepository.save(account);

            // Update transaction status to FAILED
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);

            // Publish transaction.failed event
            publishTransactionFailedFromVoid(transaction, event);
            
            // Mark event as processed
            processedEventService.markEventAsProcessed(eventId, payloadHash, "SUCCESS");
            log.info("Successfully processed hold.voided event for transaction: {}", event.getTransactionId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for transaction: {}", event.getTransactionId(), e);
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw new RuntimeException("Failed to serialize event payload", e);
        } catch (Exception e) {
            log.error("Failed to process hold.voided event for transaction: {}", event.getTransactionId(), e);
            // Mark event as processed with failed status
            processedEventService.markEventAsProcessed(eventId, "", "FAILED");
            throw e;
        }
    }

    private boolean isTransactionVoidable(Transaction transaction) {
        // Only AUTHORIZED transactions can be voided (same logic as expiry)
        return TransactionStatus.AUTHORIZED.equals(transaction.getStatus());
    }

    private void publishTransactionFailedFromVoid(Transaction transaction, HoldVoidedEvent holdEvent) {
        var payload = new FailedPayload(
                transaction.getTransactionId(),
                holdEvent.getHoldId(),
                holdEvent.getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().toString(),
                holdEvent.getReason() != null ? holdEvent.getReason() : "Hold voided"
        );

        try {
            outboxEventService.saveEvent(
                    "transaction.failed",
                    transaction.getTransactionId(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize transaction failed event payload", e);
        }
    }

    private boolean isTransactionExpirable(Transaction transaction) {
        // Only AUTHORIZED transactions can be expired
        // PENDING, SUCCESS, and FAILED transactions should not be expired
        return TransactionStatus.AUTHORIZED.equals(transaction.getStatus());
    }

    private void releaseFunds(Account account, BigDecimal amount) {
        // Release funds: available_balance += amount, reserved -= amount
        BigDecimal newAvailableBalance = account.getAvailableBalance().add(amount);
        BigDecimal newReservedBalance = account.getReserved().subtract(amount);
        
        // Validate reserved balance doesn't go negative (defensive check)
        if (newReservedBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Reserved balance would go negative for account {}, setting to zero. Reserved: {}, Amount: {}",
                    account.getAccountId(), account.getReserved(), amount);
            newReservedBalance = BigDecimal.ZERO;
        }
        
        account.setAvailableBalance(newAvailableBalance);
        account.setReserved(newReservedBalance);
    }

    private void publishTransactionFailed(Transaction transaction, HoldExpiredEvent holdEvent) {
        var payload = new FailedPayload(
                transaction.getTransactionId(),
                holdEvent.getHoldId(),
                holdEvent.getAccountId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus().toString(),
                "Hold expired"
        );

        try {
            outboxEventService.saveEvent(
                    "transaction.failed",
                    transaction.getTransactionId(),
                    objectMapper.writeValueAsString(payload)
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize transaction failed event payload", e);
        }
    }

    // Simple record for JSON serialization
    private record AuthorizedPayload(
            @NotNull Long transactionId,
            @NotNull Long holdId,
            @NotNull Long issuerAccountId,
            @NotNull Long merchantAccountId,
            @NotNull BigDecimal amount,
            @NotNull String currency,
            @NotNull String status
    ) {}

    // Simple record for JSON serialization
    private record FailedPayload(
            Long transactionId,
            Long holdId,
            Long accountId,
            BigDecimal amount,
            String currency,
            String status,
            String reason
    ) {}
}
