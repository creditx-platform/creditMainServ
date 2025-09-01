package com.creditx.main.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.creditx.main.dto.CommitTransactionRequest;
import com.creditx.main.dto.CommitTransactionResponse;
import com.creditx.main.dto.CreateTransactionRequest;
import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.service.TransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<CreateTransactionResponse> createTransaction(@Validated @RequestBody CreateTransactionRequest request) {
        log.info("=== CONTROLLER: Creating transaction for issuer: {}, merchant: {}, amount: {}", 
                request.getIssuerAccountId(), request.getMerchantAccountId(), request.getAmount());
        var response = transactionService.createInboundTransaction(request);
        log.info("=== CONTROLLER: Transaction created with ID: {}, status: {}", 
                response.getTransactionId(), response.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{id}/commit")
    public ResponseEntity<CommitTransactionResponse> commitTransaction(
            @PathVariable Long id, 
            @Validated @RequestBody CommitTransactionRequest request) {
        log.info("=== CONTROLLER: Committing transaction: {}", id);
        var response = transactionService.commitTransaction(id, request);
        log.info("=== CONTROLLER: Transaction committed with status: {}", response.getStatus());
        return ResponseEntity.ok(response);
    }
}
