package com.creditx.main.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTransactionRequest {
    @NotNull
    private Long issuerAccountId;
    @NotNull
    private Long merchantAccountId;
    @NotNull
    @Min(1)
    private BigDecimal amount;
    @Size(min = 3, max = 3)
    private String currency = "USD";
}
