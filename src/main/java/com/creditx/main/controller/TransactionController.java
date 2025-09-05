package com.creditx.main.controller;

import com.creditx.main.dto.CommitTransactionRequest;
import com.creditx.main.dto.CommitTransactionResponse;
import com.creditx.main.dto.CreateTransactionRequest;
import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Create a transaction", description = "Creates a new inbound transaction", tags = {"public"})
    public ResponseEntity<CreateTransactionResponse> createTransaction(@Validated @RequestBody CreateTransactionRequest request) {
        log.info("=== CONTROLLER: Creating transaction for issuer: {}, merchant: {}, amount: {}", 
                request.getIssuerAccountId(), request.getMerchantAccountId(), request.getAmount());
        var response = transactionService.createInboundTransaction(request);
        log.info("=== CONTROLLER: Transaction created with ID: {}, status: {}", 
                response.getTransactionId(), response.getStatus());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/{id}/commit")
    @Operation(summary = "Commit a transaction", description = "Commits a previously created transaction", tags = {"internal"})
    public ResponseEntity<CommitTransactionResponse> commitTransaction(
            @PathVariable Long id, 
            @Validated @RequestBody CommitTransactionRequest request) {
        log.info("=== CONTROLLER: Committing transaction: {}", id);
        var response = transactionService.commitTransaction(id, request);
        log.info("=== CONTROLLER: Transaction committed with status: {}", response.getStatus());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("Invalid request: {}", e.getMessage());
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException e) {
        log.error("Invalid state: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        log.error("Invalid parameter type: {}", e.getMessage());
        return ResponseEntity.badRequest().body("Invalid parameter format");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException e) {
        log.error("Internal server error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error occurred");
    }
}
