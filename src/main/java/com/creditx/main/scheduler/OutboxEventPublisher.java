package com.creditx.main.scheduler;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.creditx.main.model.OutboxEvent;
import com.creditx.main.service.KafkaPublisher;
import com.creditx.main.service.OutboxEventService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {
    private final OutboxEventService outboxEventService;
    private final KafkaPublisher kafkaPublisher;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.outbox.publish-interval}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventService.fetchPendingEvents(batchSize);

        for (OutboxEvent event : events) {
            try {
                kafkaPublisher.send(event.getAggregateId().toString(), event.getPayload());
                outboxEventService.markAsPublished(event);
            } catch (Exception e) {
                outboxEventService.markAsFailed(event);
            }
        }
    }
}
