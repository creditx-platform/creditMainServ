package com.creditx.main.repository;

import com.creditx.main.model.TransactionEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionEntryRepository extends JpaRepository<TransactionEntry, Long> {

  List<TransactionEntry> findByTransactionTransactionId(Long transactionId);
}
