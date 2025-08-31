package com.creditx.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.creditx.main.dto.CreateHoldRequest;
import com.creditx.main.dto.CreateHoldResponse;
import com.creditx.main.dto.CreateTransactionRequest;
import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.model.Account;
import com.creditx.main.model.AccountStatus;
import com.creditx.main.model.AccountType;
import com.creditx.main.model.HoldStatus;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.model.TransactionType;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.service.impl.TransactionServiceImpl;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Account issuerAccount;
    private Account merchantAccount;
    private CreateTransactionRequest validRequest;
    private Transaction savedTransaction;

    @BeforeEach
    void setUp() {
        // Set the credit hold service URL using reflection
        ReflectionTestUtils.setField(transactionService, "creditHoldServiceUrl", "http://localhost:8081");

        // Setup test data
        issuerAccount = Account.builder()
                .accountId(1L)
                .customerId(100L)
                .type(AccountType.ISSUER)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("1000.00"))
                .build();

        merchantAccount = Account.builder()
                .accountId(2L)
                .customerId(200L)
                .type(AccountType.MERCHANT)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("500.00"))
                .build();

        validRequest = new CreateTransactionRequest();
        validRequest.setIssuerAccountId(1L);
        validRequest.setMerchantAccountId(2L);
        validRequest.setAmount(new BigDecimal("150.75"));
        validRequest.setCurrency("USD");

        savedTransaction = Transaction.builder()
                .transactionId(999L)
                .type(TransactionType.INBOUND)
                .status(TransactionStatus.PENDING)
                .accountId(1L)
                .amount(new BigDecimal("150.75"))
                .currency("USD")
                .build();
    }

    @Test
    void createInboundTransaction_success() {
        // Given: Valid accounts and transaction request
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));
        given(transactionRepository.save(any(Transaction.class))).willReturn(savedTransaction);
        
        // Mock hold response with hold_id
        CreateHoldResponse holdResponse = CreateHoldResponse.builder()
                .holdId(12345L)
                .status(HoldStatus.AUTHORIZED)
                .build();
        given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(CreateHoldResponse.class)))
                .willReturn(ResponseEntity.ok(holdResponse));

        // When: Creating inbound transaction
        CreateTransactionResponse response = transactionService.createInboundTransaction(validRequest);

        // Then: Response should be successful
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo(999L);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);

        // Verify transaction was saved twice: first initial save, then with hold_id
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(transactionCaptor.capture());
        
        Transaction capturedTransaction = transactionCaptor.getValue();
        assertThat(capturedTransaction.getType()).isEqualTo(TransactionType.INBOUND);
        assertThat(capturedTransaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(capturedTransaction.getAccountId()).isEqualTo(1L);
        assertThat(capturedTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("150.75"));
        assertThat(capturedTransaction.getCurrency()).isEqualTo("USD");

        // Verify outbox event was created
        verify(outboxEventService).saveEvent(eq("transaction.initiated"), eq(999L), anyString());

        // Verify hold request was sent to CreditHoldServ
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<CreateHoldRequest>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://localhost:8081/holds"),
                httpEntityCaptor.capture(),
                eq(CreateHoldResponse.class)
        );

        // Verify hold request content
        HttpEntity<CreateHoldRequest> capturedEntity = httpEntityCaptor.getValue();
        CreateHoldRequest holdRequest = capturedEntity.getBody();
        assertThat(holdRequest).isNotNull();
        if (holdRequest != null) {
            assertThat(holdRequest.getTransactionId()).isEqualTo(999L);
            assertThat(holdRequest.getIssuerAccountId()).isEqualTo(1L);
            assertThat(holdRequest.getMerchantAccountId()).isEqualTo(2L);
            assertThat(holdRequest.getAmount()).isEqualByComparingTo(new BigDecimal("150.75"));
            assertThat(holdRequest.getCurrency()).isEqualTo("USD");
        }

        // Verify HTTP headers
        assertThat(capturedEntity.getHeaders().getContentType()).isNotNull();
        var contentType = capturedEntity.getHeaders().getContentType();
        if (contentType != null) {
            assertThat(contentType.toString()).contains("application/json");
        }
    }

    @Test
    void createInboundTransaction_issuerAccountNotFound() {
        // Given: Issuer account doesn't exist
        given(accountRepository.findById(1L)).willReturn(Optional.empty());

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Issuer account not found");

        // Verify no transaction was saved
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), any(), anyString());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void createInboundTransaction_merchantAccountNotFound() {
        // Given: Merchant account doesn't exist
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.empty());

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Merchant account not found");

        // Verify no transaction was saved
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), any(), anyString());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void createInboundTransaction_invalidIssuerAccountType() {
        // Given: Issuer account has wrong type
        Account invalidIssuerAccount = Account.builder()
                .accountId(1L)
                .type(AccountType.MERCHANT) // Wrong type
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("1000.00"))
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(invalidIssuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Issuer account type invalid");

        // Verify no transaction was saved
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), any(), anyString());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void createInboundTransaction_invalidMerchantAccountType() {
        // Given: Merchant account has wrong type
        Account invalidMerchantAccount = Account.builder()
                .accountId(2L)
                .type(AccountType.ISSUER) // Wrong type
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("500.00"))
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(invalidMerchantAccount));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Merchant account type invalid");

        // Verify no transaction was saved
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), any(), anyString());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void createInboundTransaction_inactiveIssuerAccount() {
        // Given: Issuer account is blocked
        Account blockedIssuerAccount = Account.builder()
                .accountId(1L)
                .type(AccountType.ISSUER)
                .status(AccountStatus.BLOCKED)
                .availableBalance(new BigDecimal("1000.00"))
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(blockedIssuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accounts must be ACTIVE");

        // Verify no transaction was saved
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), any(), anyString());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void createInboundTransaction_inactiveMerchantAccount() {
        // Given: Merchant account is closed
        Account closedMerchantAccount = Account.builder()
                .accountId(2L)
                .type(AccountType.MERCHANT)
                .status(AccountStatus.CLOSED)
                .availableBalance(new BigDecimal("500.00"))
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(closedMerchantAccount));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Accounts must be ACTIVE");

        // Verify no transaction was saved
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), any(), anyString());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void createInboundTransaction_insufficientBalance() {
        // Given: Issuer account has insufficient balance
        Account lowBalanceIssuerAccount = Account.builder()
                .accountId(1L)
                .type(AccountType.ISSUER)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("100.00")) // Less than requested amount
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(lowBalanceIssuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));

        // When & Then: Should throw exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Insufficient available balance");

        // Verify no transaction was saved
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(outboxEventService, never()).saveEvent(anyString(), any(), anyString());
        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }

    @Test
    void createInboundTransaction_holdServiceFailure() {
        // Given: Valid accounts but hold service call fails
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));
        given(transactionRepository.save(any(Transaction.class))).willReturn(savedTransaction);
        given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .willThrow(new RestClientException("Connection refused"));

        // When & Then: Should throw runtime exception
        assertThatThrownBy(() -> transactionService.createInboundTransaction(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to send hold request to CreditHoldServ")
                .hasCauseInstanceOf(RestClientException.class);

        // Verify transaction was saved (since failure happens after transaction creation)
        verify(transactionRepository).save(any(Transaction.class));
        
        // Verify outbox event was created (since failure happens after event creation)
        verify(outboxEventService).saveEvent(eq("transaction.initiated"), eq(999L), anyString());

        // Verify hold request was attempted
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void createInboundTransaction_exactBalanceMatch() {
        // Given: Issuer account balance exactly matches transaction amount
        Account exactBalanceIssuerAccount = Account.builder()
                .accountId(1L)
                .type(AccountType.ISSUER)
                .status(AccountStatus.ACTIVE)
                .availableBalance(new BigDecimal("150.75")) // Exactly the requested amount
                .build();

        given(accountRepository.findById(1L)).willReturn(Optional.of(exactBalanceIssuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));
        given(transactionRepository.save(any(Transaction.class))).willReturn(savedTransaction);
        given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .willReturn(ResponseEntity.ok("Success"));

        // When: Creating inbound transaction
        CreateTransactionResponse response = transactionService.createInboundTransaction(validRequest);

        // Then: Should succeed
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo(999L);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.PENDING);

        // Verify all services were called
        verify(transactionRepository).save(any(Transaction.class));
        verify(outboxEventService).saveEvent(anyString(), any(), anyString());
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void createInboundTransaction_verifyOutboxEventPayload() {
        // Given: Valid setup
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));
        given(transactionRepository.save(any(Transaction.class))).willReturn(savedTransaction);
        
        // Mock hold response
        CreateHoldResponse holdResponse = CreateHoldResponse.builder()
                .holdId(12345L)
                .status(HoldStatus.AUTHORIZED)
                .build();
        given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(CreateHoldResponse.class)))
                .willReturn(ResponseEntity.ok(holdResponse));

        // When: Creating inbound transaction
        transactionService.createInboundTransaction(validRequest);

        // Then: Verify outbox event contains correct payload structure
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventService).saveEvent(
                eq("transaction.initiated"), 
                eq(999L), 
                payloadCaptor.capture()
        );

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"transactionId\":999");
        assertThat(payload).contains("\"issuerAccountId\":1");
        assertThat(payload).contains("\"merchantAccountId\":2");
        assertThat(payload).contains("\"amount\":150.75");
        assertThat(payload).contains("\"currency\":\"USD\"");
    }

    @Test
    void createInboundTransaction_verifyHoldIdUpdate() {
        // Given: Valid accounts and transaction request
        given(accountRepository.findById(1L)).willReturn(Optional.of(issuerAccount));
        given(accountRepository.findById(2L)).willReturn(Optional.of(merchantAccount));
        given(transactionRepository.save(any(Transaction.class))).willReturn(savedTransaction);
        
        // Mock hold response with hold_id
        CreateHoldResponse holdResponse = CreateHoldResponse.builder()
                .holdId(12345L)
                .status(HoldStatus.AUTHORIZED)
                .build();
        given(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(CreateHoldResponse.class)))
                .willReturn(ResponseEntity.ok(holdResponse));

        // When: Creating inbound transaction
        transactionService.createInboundTransaction(validRequest);

        // Then: Verify transaction was saved twice (initial save + hold_id update)
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(2)).save(transactionCaptor.capture());
        
        // Verify the final transaction has the hold_id set
        List<Transaction> savedTransactions = transactionCaptor.getAllValues();
        Transaction finalTransaction = savedTransactions.get(1); // Second save
        assertThat(finalTransaction.getHoldId()).isEqualTo(12345L);
    }
}
