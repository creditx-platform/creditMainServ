package com.creditx.main.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.creditx.main.model.Transaction;
import com.creditx.main.model.TransactionStatus;
import com.creditx.main.model.TransactionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {

  private Long transactionId;
  private TransactionType type;
  private TransactionStatus status;
  private Long accountId;
  private Long holdId;
  private Long merchantId;
  private BigDecimal amount;
  private String currency;
  private Instant createdAt;
  private Instant updatedAt;

  public static TransactionDTO fromEntity(Transaction t) {
    if (t == null) {
      return null;
    }
    return TransactionDTO.builder()
        .transactionId(t.getTransactionId())
        .type(t.getType())
        .status(t.getStatus())
        .accountId(t.getAccountId())
        .holdId(t.getHoldId())
        .merchantId(t.getMerchantId())
        .amount(t.getAmount())
        .currency(t.getCurrency())
        .createdAt(t.getCreatedAt())
        .updatedAt(t.getUpdatedAt())
        .build();
  }
}
