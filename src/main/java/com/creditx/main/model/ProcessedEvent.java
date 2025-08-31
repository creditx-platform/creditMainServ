package com.creditx.main.model;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "CMS_PROCESSED_EVENTS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @Column(name = "EVENT_ID", length = 100)
    private String eventId;

    @Column(name = "PAYLOAD_HASH", length = 128)
    private String payloadHash;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "PROCESSED_AT", insertable = false, updatable = false)
    private Instant processedAt;
}
