package com.creditx.main;

import com.creditx.main.model.OutboxEvent;
import com.creditx.main.repository.OutboxEventRepository;
import com.creditx.main.scheduler.OutboxEventPublishingScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestChannelBinderConfiguration.class)
public class OutboxStreamIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventPublishingScheduler outboxEventPublisher;

    @Autowired
    private OutputDestination outputDestination;

    @Test
    @Transactional
    void testOutboxEventPublishedToStream() throws Exception {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType("TRANSACTION_CREATED")
                .aggregateId(123L)
                .payload("{\"transactionId\":\"txn-123\",\"amount\":100.50}")
                .status("PENDING")
                .build();

        OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
        assertThat(savedEvent.getEventId()).isNotNull();
        assertThat(savedEvent.getStatus()).isEqualTo("PENDING");

        outboxEventPublisher.publishPendingEvents();

        OutboxEvent updatedEvent = outboxEventRepository.findById(savedEvent.getEventId()).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo("PUBLISHED");
        assertThat(updatedEvent.getPublishedAt()).isNotNull();

        assertStreamMessageReceived("{\"transactionId\":\"txn-123\",\"amount\":100.50}");
    }

    @Test
    @Transactional
    void testMultipleOutboxEventsPublishedToStream() throws Exception {
        OutboxEvent event1 = OutboxEvent.builder()
                .eventType("TRANSACTION_CREATED")
                .aggregateId(100L)
                .payload("{\"transactionId\":\"txn-100\",\"amount\":50.00}")
                .status("PENDING")
                .build();

        OutboxEvent event2 = OutboxEvent.builder()
                .eventType("TRANSACTION_UPDATED")
                .aggregateId(200L)
                .payload("{\"transactionId\":\"txn-200\",\"amount\":75.00}")
                .status("PENDING")
                .build();

        outboxEventRepository.save(event1);
        outboxEventRepository.save(event2);

        outboxEventPublisher.publishPendingEvents();

        assertStreamMessageReceived("txn-100");
        assertStreamMessageReceived("txn-200");
    }

    private void assertStreamMessageReceived(String expectedContent) {
        Message<byte[]> received = outputDestination.receive(2000, "transactions-out-0");

        assertThat(received).isNotNull();
        String messagePayload = new String(received.getPayload());
        assertThat(messagePayload).contains(expectedContent);
    }
}
