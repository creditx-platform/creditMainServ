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
@Table(name = "MAIN_TRANSACTIONS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_seq_gen")
    @SequenceGenerator(name = "transaction_seq_gen", sequenceName = "MAIN_TXN_SEQ", allocationSize = 1)
    @Column(name = "TRANSACTION_ID")
    private Long transactionId;

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Column(name = "MERCHANT_ID")
    private Long merchantId;

    @Column(name = "AMOUNT", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "INITIATED_AT", nullable = false, updatable = false)
    @Builder.Default
    private Instant initiatedAt = Instant.now();

    @Column(name = "COMPLETED_AT")
    private Instant completedAt;

    @Column(name = "UPDATED_AT", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "PROMO_APPLIED", nullable = false, length = 1)
    @Builder.Default
    private String promoApplied = "N";

    @Column(name = "EXTERNAL_REF_ID", length = 100, unique = true)
    private String externalRefId;
}
