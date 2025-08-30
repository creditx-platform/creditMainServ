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
@Table(name = "CMS_ACCOUNTS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_seq_gen")
    @SequenceGenerator(name = "account_seq_gen", sequenceName = "CMS_ACCT_SEQ", allocationSize = 1)
    @Column(name = "ACCOUNT_ID")
    private Long accountId;

    @Column(name = "CUSTOMER_ID", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACCOUNT_TYPE", nullable = false, length = 20)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "AVAILABLE_BALANCE", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "RESERVED", nullable = false, precision = 20, scale = 2)
    @Builder.Default
    private BigDecimal reserved = BigDecimal.ZERO;

    @Column(name = "CREDIT_LIMIT", nullable = false, precision = 20, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", insertable = false, updatable = false)
    private Instant updatedAt;
}
