package com.creditx.main.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.creditx.main.model.Account;
import com.creditx.main.model.AccountStatus;
import com.creditx.main.model.AccountType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDTO {
    private Long accountId;
    private Long customerId;
    private AccountType type;
    private AccountStatus status;
    private BigDecimal availableBalance;
    private BigDecimal reserved;
    private BigDecimal creditLimit;
    private Instant createdAt;
    private Instant updatedAt;

    public static AccountDTO fromEntity(Account a) {
        if (a == null) return null;
        return AccountDTO.builder()
                .accountId(a.getAccountId())
                .customerId(a.getCustomerId())
                .type(a.getType())
                .status(a.getStatus())
                .availableBalance(a.getAvailableBalance())
                .reserved(a.getReserved())
                .creditLimit(a.getCreditLimit())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
