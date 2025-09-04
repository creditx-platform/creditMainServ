package com.creditx.main.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creditx.main.dto.CommitTransactionResponse;
import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.service.TransactionService;

import static org.mockito.BDDMockito.given;

@WebMvcTest(controllers = TransactionController.class)
class TransactionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TransactionService transactionService;

    @Test
    void createTransaction_success() throws Exception {
        given(transactionService.createInboundTransaction(any())).willReturn(
                CreateTransactionResponse.builder()
                        .transactionId(999L)
                        .status(TransactionStatus.PENDING)
                        .build());

        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value(999))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createTransaction_validationError_missingIssuerAccountId() throws Exception {
        String requestBody = """
            {
                "merchantAccountId": 2,
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_validationError_missingMerchantAccountId() throws Exception {
        String requestBody = """
            {
                "issuerAccountId": 1,
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_validationError_missingAmount() throws Exception {
        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_validationError_invalidAmount() throws Exception {
        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 0.00,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_validationError_negativeAmount() throws Exception {
        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": -50.00,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_validationError_invalidCurrency() throws Exception {
        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 100.00,
                "currency": "INVALID"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_validationError_emptyCurrency() throws Exception {
        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 100.00,
                "currency": ""
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_serviceException() throws Exception {
        given(transactionService.createInboundTransaction(any())).willThrow(
                new IllegalArgumentException("Account not found"));

        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account not found"));
    }

    @Test
    void createTransaction_insufficientBalanceException() throws Exception {
        given(transactionService.createInboundTransaction(any())).willThrow(
                new IllegalArgumentException("Insufficient available balance"));

        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 1000.00,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Insufficient available balance"));
    }

    @Test
    void commitTransaction_success() throws Exception {
        given(transactionService.commitTransaction(eq(999L), any())).willReturn(
                CommitTransactionResponse.builder()
                        .transactionId(999L)
                        .status(TransactionStatus.SUCCESS)
                        .message("Transaction committed successfully")
                        .build());

        String requestBody = """
            {
                "holdId": 12345
            }
            """;

        mockMvc.perform(post("/api/transactions/999/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(999))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Transaction committed successfully"));
    }

    @Test
    void commitTransaction_validationError_missingHoldId() throws Exception {
        String requestBody = """
            {
            }
            """;

        mockMvc.perform(post("/api/transactions/999/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commitTransaction_transactionNotFound() throws Exception {
        given(transactionService.commitTransaction(eq(999L), any())).willThrow(
                new IllegalArgumentException("Transaction not found: 999"));

        String requestBody = """
            {
                "holdId": 12345
            }
            """;

        mockMvc.perform(post("/api/transactions/999/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Transaction not found: 999"));
    }

    @Test
    void commitTransaction_wrongStatus() throws Exception {
        given(transactionService.commitTransaction(eq(999L), any())).willThrow(
                new IllegalStateException("Transaction is not in AUTHORIZED status"));

        String requestBody = """
            {
                "holdId": 12345
            }
            """;

        mockMvc.perform(post("/api/transactions/999/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(content().string("Transaction is not in AUTHORIZED status"));
    }

    @Test
    void commitTransaction_invalidPathVariable() throws Exception {
        String requestBody = """
            {
                "holdId": 12345
            }
            """;

        mockMvc.perform(post("/api/transactions/invalid/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTransaction_withDefaultCurrency() throws Exception {
        given(transactionService.createInboundTransaction(any())).willReturn(
                CreateTransactionResponse.builder()
                        .transactionId(999L)
                        .status(TransactionStatus.PENDING)
                        .build());

        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 100.00
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value(999))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createTransaction_withLargeAmount() throws Exception {
        given(transactionService.createInboundTransaction(any())).willReturn(
                CreateTransactionResponse.builder()
                        .transactionId(999L)
                        .status(TransactionStatus.PENDING)
                        .build());

        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 9999.99,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value(999))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createTransaction_withDecimalPrecision() throws Exception {
        given(transactionService.createInboundTransaction(any())).willReturn(
                CreateTransactionResponse.builder()
                        .transactionId(999L)
                        .status(TransactionStatus.PENDING)
                        .build());

        String requestBody = """
            {
                "issuerAccountId": 1,
                "merchantAccountId": 2,
                "amount": 123.456,
                "currency": "USD"
            }
            """;

        mockMvc.perform(post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.transactionId").value(999))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
