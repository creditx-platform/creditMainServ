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
public class TransactionFailedEvent {

  private Long transactionId;
  private Long holdId;
  private Long accountId;
  private BigDecimal amount;
  private String currency;
  private String status;
  private String reason;
  private Instant failedAt;
}
