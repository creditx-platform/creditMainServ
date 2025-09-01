package com.creditx.main;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.creditx.main.controller.TransactionController;
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
                                CreateTransactionResponse.builder().transactionId(999L).status(TransactionStatus.PENDING).build());

                String body = "{\"issuerAccountId\":1,\"merchantAccountId\":2,\"amount\":100.00,\"currency\":\"USD\"}";

                mockMvc.perform(post("/api/transactions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.transactionId").value(999))
                                .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        void commitTransaction_success() throws Exception {
                given(transactionService.commitTransaction(any(Long.class), any())).willReturn(
                                CommitTransactionResponse.builder()
                                        .transactionId(999L)
                                        .status(TransactionStatus.SUCCESS)
                                        .message("Transaction committed successfully")
                                        .build());

                String body = "{\"holdId\":12345}";

                mockMvc.perform(post("/api/transactions/999/commit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.transactionId").value(999))
                                .andExpect(jsonPath("$.status").value("SUCCESS"))
                                .andExpect(jsonPath("$.message").value("Transaction committed successfully"));
        }
}

