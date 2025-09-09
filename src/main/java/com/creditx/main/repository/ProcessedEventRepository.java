package com.creditx.main.repository;

import com.creditx.main.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

  boolean existsByEventId(String eventId);

  boolean existsByPayloadHash(String payloadHash);
}
