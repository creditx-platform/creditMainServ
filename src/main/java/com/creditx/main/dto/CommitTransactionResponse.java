package com.creditx.main.dto;

import com.creditx.main.model.TransactionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommitTransactionResponse {
    private Long transactionId;
    private TransactionStatus status;
    private String message;
}
