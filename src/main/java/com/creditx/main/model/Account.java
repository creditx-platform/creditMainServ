package com.creditx.main.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "MAIN_ACCOUNTS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq_gen")
    @SequenceGenerator(name = "account_seq_gen", sequenceName = "MAIN_ACCT_SEQ", allocationSize = 1)
    @Column(name = "ACCOUNT_ID")
    private Long accountId;

    @Column(name = "CUSTOMER_ID", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "AVAILABLE_BALANCE", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "CREDIT_LIMIT", nullable = false, precision = 10, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "UPDATED_AT", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
