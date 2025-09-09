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
public class HoldExpiredEvent {

  private Long holdId;
  private Long transactionId;
  private Long accountId;
  private BigDecimal amount;
  private String status;
  private Instant expiresAt;
}
