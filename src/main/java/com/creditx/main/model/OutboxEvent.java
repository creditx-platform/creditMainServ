package com.creditx.main.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Lob;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "MAIN_OUTBOX_EVENTS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_seq_gen")
    @SequenceGenerator(name = "outbox_seq_gen", sequenceName = "MAIN_OUTBOX_SEQ", allocationSize = 1)
    @Column(name = "EVENT_ID")
    private Long eventId;

    @Column(name = "EVENT_TYPE", nullable = false)
    private String eventType;

    @Column(name = "AGGREGATE_ID")
    private Long aggregateId;

    @Lob
    @Column(name = "PAYLOAD", nullable = false)
    private String payload;

    @Column(name = "STATUS", nullable = false)
    private String status;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;
}