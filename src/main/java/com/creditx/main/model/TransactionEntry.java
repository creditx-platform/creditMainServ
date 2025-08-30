package com.creditx.main.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CMS_TRANSACTION_ENTRIES")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "txn_entry_seq_gen")
    @SequenceGenerator(name = "txn_entry_seq_gen", sequenceName = "CMS_ENTRY_SEQ", allocationSize = 1)
    @Column(name = "ENTRY_ID")
    private Long entryId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "TRANSACTION_ID", nullable = false)
    private Transaction transaction;

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Column(name = "AMOUNT", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;
}
