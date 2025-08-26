package com.creditx.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creditx.main.model.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

}