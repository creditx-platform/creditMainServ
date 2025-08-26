package com.creditx.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creditx.main.model.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

}