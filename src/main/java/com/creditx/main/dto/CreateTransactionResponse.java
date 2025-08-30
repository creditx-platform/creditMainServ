package com.creditx.main.dto;

import com.creditx.main.model.TransactionStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CreateTransactionResponse {
    Long transactionId;
    TransactionStatus status;
}
