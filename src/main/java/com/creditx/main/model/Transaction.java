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
@Table(name = "CMS_TRANSACTIONS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_seq_gen")
    @SequenceGenerator(name = "transaction_seq_gen", sequenceName = "CMS_TXN_SEQ", allocationSize = 1)
    @Column(name = "TRANSACTION_ID")
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE", nullable = false, length = 50)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATE", nullable = false, length = 50)
    private TransactionStatus status;

    @Column(name = "ACCOUNT_ID")
    private Long accountId;

    @Column(name = "HOLD_ID")
    private Long holdId;

    @Column(name = "MERCHANT_ID")
    private Long merchantId;

    @Column(name = "AMOUNT", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "CREATED_AT", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", insertable = false, updatable = false)
    private Instant updatedAt;
}
