package com.creditx.main.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHoldRequest {

  private Long transactionId;
  private Long issuerAccountId;
  private Long merchantAccountId;
  private BigDecimal amount;
  private String currency;
}
