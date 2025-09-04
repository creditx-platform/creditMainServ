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
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventServiceImpl implements OutboxEventService {

    private final OutboxEventRepository repository;

    @Override
    @Transactional
    public OutboxEvent saveEvent(String eventType, Long aggregateId, String payload) {
        log.debug("Saving outbox event: type={}, aggregateId={}", eventType, aggregateId);
        OutboxEvent event = OutboxEvent.builder()
                .eventType(eventType)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .build();
        OutboxEvent savedEvent = repository.save(event);
        log.info("Outbox event saved with ID: {}", savedEvent.getEventId());
        return savedEvent;
    }

    @Override
    public List<OutboxEvent> fetchPendingEvents(int limit) {
        log.debug("Fetching pending outbox events with limit: {}", limit);
        List<OutboxEvent> pendingEvents = repository.findAll()
                .stream()
                .filter(e -> OutboxEventStatus.PENDING.equals(e.getStatus()))
                .limit(limit)
                .toList();
        log.debug("Found {} pending outbox events", pendingEvents.size());
        return pendingEvents;
    }

    @Override
    @Transactional
    public void markAsPublished(OutboxEvent event) {
        log.debug("Marking outbox event {} as published", event.getEventId());
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        repository.save(event);
        log.info("Outbox event {} marked as published", event.getEventId());
    }

    @Override
    @Transactional
    public void markAsFailed(OutboxEvent event) {
        log.warn("Marking outbox event {} as failed", event.getEventId());
        event.setStatus(OutboxEventStatus.FAILED);
        repository.save(event);
        log.debug("Outbox event {} marked as failed", event.getEventId());
    }
}