package com.creditx.main.dto;

import java.math.BigDecimal;
import java.time.Instant;

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
    private Long holdId;
    private Long issuerAccountId;
    private Long merchantAccountId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant postedAt;
}
