package com.creditx.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.creditx.main.model.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long>{
    
}
