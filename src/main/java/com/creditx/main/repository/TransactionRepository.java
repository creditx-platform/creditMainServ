package com.creditx.main.repository;

import com.creditx.main.model.Transaction;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

  List<Transaction> findByAccountId(Long accountId);

  Optional<Transaction> findByHoldId(Long holdId);
}
