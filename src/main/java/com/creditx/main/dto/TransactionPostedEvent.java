package com.creditx.main.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.creditx.main.model.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionPostedEvent {
    private Long transactionId;
    private TransactionType type;
    private Long holdId;
    private Long issuerAccountId;
    private Long merchantAccountId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant createdAt;
    private Instant postedAt;
}
