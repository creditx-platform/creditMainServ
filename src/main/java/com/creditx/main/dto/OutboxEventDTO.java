package com.creditx.main.dto;

import java.time.Instant;

import com.creditx.main.model.OutboxEvent;
import com.creditx.main.model.OutboxEventStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventDTO {
    private Long eventId;
    private String eventType;
    private Long aggregateId;
    private String payload;
    private OutboxEventStatus status;
    private Instant createdAt;
    private Instant publishedAt;

    public static OutboxEventDTO fromEntity(OutboxEvent e) {
        if (e == null) return null;
        return OutboxEventDTO.builder()
                .eventId(e.getEventId())
                .eventType(e.getEventType())
                .aggregateId(e.getAggregateId())
                .payload(e.getPayload())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .publishedAt(e.getPublishedAt())
                .build();
    }
}
