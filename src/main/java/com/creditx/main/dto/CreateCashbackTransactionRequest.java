package com.creditx.main.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCashbackTransactionRequest {
    @NotNull
    private Long issuerAccountId; // account receiving cashback (credit)
    @NotNull
    private Long merchantAccountId; // source of funds or platform
    @NotNull
    @Min(1)
    private BigDecimal amount;
    @Size(min = 3, max = 3)
    private String currency = "USD";
}
