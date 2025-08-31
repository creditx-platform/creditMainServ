package com.creditx.main.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitTransactionRequest {
    @NotNull
    private Long transactionId;
    @NotNull
    private Long holdId;
}
