package com.creditx.main.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.creditx.main.model.OutboxEvent;
import com.creditx.main.model.OutboxEventStatus;
import com.creditx.main.repository.OutboxEventRepository;
import com.creditx.main.service.OutboxEventService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxEventServiceImpl implements OutboxEventService {

    private final OutboxEventRepository repository;

    @Override
    @Transactional
    public OutboxEvent saveEvent(String eventType, Long aggregateId, String payload) {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .build();
        return repository.save(event);
    }

    @Override
    public List<OutboxEvent> fetchPendingEvents(int limit) {
        return repository.findAll()
                .stream()
                .filter(e -> OutboxEventStatus.PENDING.equals(e.getStatus()))
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional
    public void markAsPublished(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        repository.save(event);
    }

    @Override
    @Transactional
    public void markAsFailed(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.FAILED);
        repository.save(event);
    }
}