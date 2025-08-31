package com.creditx.main.service;

import com.creditx.main.dto.CommitTransactionRequest;
import com.creditx.main.dto.CommitTransactionResponse;
import com.creditx.main.dto.CreateTransactionRequest;
import com.creditx.main.dto.CreateTransactionResponse;

public interface TransactionService {
    CreateTransactionResponse createInboundTransaction(CreateTransactionRequest request);
    CommitTransactionResponse commitTransaction(CommitTransactionRequest request);
}
