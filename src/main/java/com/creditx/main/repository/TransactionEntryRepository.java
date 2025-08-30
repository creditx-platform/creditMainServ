package com.creditx.main.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creditx.main.model.TransactionEntry;

public interface TransactionEntryRepository extends JpaRepository<TransactionEntry, Long> {
    List<TransactionEntry> findByTransactionTransactionId(Long transactionId);
}
