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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.creditx.main.dto.CreateHoldResponse;
import com.creditx.main.dto.CreateTransactionRequest;
import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.dto.CommitTransactionRequest;
import com.creditx.main.dto.CommitTransactionResponse;
import com.creditx.main.model.Account;
import com.creditx.main.model.AccountStatus;
import com.creditx.main.model.AccountType;
import com.creditx.main.model.HoldStatus;
import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.model.TransactionType;
import com.creditx.main.repository.AccountRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.repository.TransactionEntryRepository;
import com.creditx.main.service.OutboxEventService;
import com.creditx.main.tracing.TransactionSpanTagger;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @Mock
    private TransactionSpanTagger transactionSpanTagger;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(transactionService, "creditHoldServiceUrl", "http://localhost:8081");
    }

    @Test
    void shouldCreateInboundTransactionSuccessfully() {
        // given
        CreateTransactionRequest request = createTransactionRequest();
        Account issuer = createIssuerAccount();
        Account merchant = createMerchantAccount();
        Transaction savedTransaction = createTransaction(1L, TransactionStatus.PENDING);
        CreateHoldResponse holdResponse = createHoldResponse(100L, "AUTHORIZED");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(issuer));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(merchant));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(CreateHoldResponse.class)))
                .thenReturn(ResponseEntity.ok(holdResponse));

        // when
        CreateTransactionResponse response = transactionService.createInboundTransaction(request);

        // then
        assertThat(response.getTransactionId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.AUTHORIZED);

        verify(accountRepository, times(1)).findById(1L);
        verify(accountRepository, times(1)).findById(2L);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(outboxEventService, times(1)).saveEvent(anyString(), eq(1L), anyString());
        verify(restTemplate, times(1)).postForEntity(anyString(), any(HttpEntity.class), eq(CreateHoldResponse.class));
    }

    @Test
    void shouldThrowExceptionWhenIssuerAccountNotFound() {
        // given
        CreateTransactionRequest request = createTransactionRequest();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> transactionService.createInboundTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Issuer account not found");

        verify(accountRepository, times(1)).findById(1L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenMerchantAccountNotFound() {
        // given
        CreateTransactionRequest request = createTransactionRequest();
        Account issuer = createIssuerAccount();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(issuer));
        when(accountRepository.findById(2L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> transactionService.createInboundTransaction(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Merchant account not found");

        verify(accountRepository, times(1)).findById(1L);
        verify(accountRepository, times(1)).findById(2L);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldCommitTransactionSuccessfully() {
        // given
        Long transactionId = 1L;
        CommitTransactionRequest request = CommitTransactionRequest.builder()
                .transactionId(transactionId)
                .holdId(100L)
                .build();

        Transaction transaction = createTransaction(transactionId, TransactionStatus.AUTHORIZED);
        transaction.setHoldId(100L);
        Account issuer = createIssuerAccount();
        Account merchant = createMerchantAccount();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(issuer));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(merchant));

        // when
        CommitTransactionResponse response = transactionService.commitTransaction(transactionId, request);

        // then
        assertThat(response.getTransactionId()).isEqualTo(transactionId);
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        verify(transactionRepository, times(1)).findById(transactionId);
        verify(transactionRepository, times(1)).save(transaction);
        verify(outboxEventService, times(1)).saveEvent(anyString(), eq(transactionId), anyString());
    }

    @Test
    void shouldThrowExceptionWhenCommittingNonAuthorizedTransaction() {
        // given
        Long transactionId = 1L;
        CommitTransactionRequest request = CommitTransactionRequest.builder()
                .transactionId(transactionId)
                .holdId(100L)
                .build();

        Transaction transaction = createTransaction(transactionId, TransactionStatus.PENDING);
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        // when & then
        assertThatThrownBy(() -> transactionService.commitTransaction(transactionId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Transaction must be in AUTHORIZED state to commit");

        verify(transactionRepository, times(1)).findById(transactionId);
        verify(transactionRepository, never()).save(any());
    }

    private CreateTransactionRequest createTransactionRequest() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setIssuerAccountId(1L);
        request.setMerchantAccountId(2L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        return request;
    }

    private Account createIssuerAccount() {
        Account account = new Account();
        account.setAccountId(1L);
        account.setType(AccountType.ISSUER);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAvailableBalance(new BigDecimal("1000.00"));
        account.setReserved(BigDecimal.ZERO);
        return account;
    }

    private Account createMerchantAccount() {
        Account account = new Account();
        account.setAccountId(2L);
        account.setType(AccountType.MERCHANT);
        account.setStatus(AccountStatus.ACTIVE);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setReserved(BigDecimal.ZERO);
        return account;
    }

    private Transaction createTransaction(Long id, TransactionStatus status) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(id);
        transaction.setType(TransactionType.INBOUND);
        transaction.setStatus(status);
        transaction.setAccountId(1L);
        transaction.setMerchantId(2L);
        transaction.setAmount(new BigDecimal("100.00"));
        transaction.setCurrency("USD");
        return transaction;
    }

    private CreateHoldResponse createHoldResponse(Long holdId, String status) {
        CreateHoldResponse response = new CreateHoldResponse();
        response.setHoldId(holdId);
        response.setStatus(HoldStatus.valueOf(status));
        return response;
    }
}