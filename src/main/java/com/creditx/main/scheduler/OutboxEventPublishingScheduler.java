package com.creditx.main.scheduler;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.creditx.main.messaging.OutboxStreamPublisher;
import com.creditx.main.model.OutboxEvent;
import com.creditx.main.service.OutboxEventService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxEventPublishingScheduler {
    private final OutboxEventService outboxEventService;
    private final OutboxStreamPublisher outboxStreamPublisher;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventService.fetchPendingEvents(batchSize);

        for (OutboxEvent event : events) {
            try {
                // Only publish transaction.authorized events to the transactions topic
                // Other event types (transaction.initiated, transaction.posted, etc.) are stored but not published
                if ("transaction.authorized".equals(event.getEventType())) {
                    outboxStreamPublisher.publish(event.getAggregateId().toString(), event.getPayload());
                }
                outboxEventService.markAsPublished(event);
            } catch (Exception e) {
                outboxEventService.markAsFailed(event);
            }
        }
    }
}
