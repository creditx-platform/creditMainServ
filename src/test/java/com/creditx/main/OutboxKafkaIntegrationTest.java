package com.creditx.main;

import com.creditx.main.model.OutboxEvent;
import com.creditx.main.repository.OutboxEventRepository;
import com.creditx.main.scheduler.OutboxEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=true"
})
class OutboxKafkaIntegrationTest {

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpass");

    @Container
    static final ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:latest");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Oracle configuration
        registry.add("ORACLE_HOST", oracle::getHost);
        registry.add("ORACLE_PORT", () -> oracle.getFirstMappedPort().toString());
        registry.add("ORACLE_SVC", oracle::getDatabaseName);
        registry.add("ORACLE_USER", oracle::getUsername);
        registry.add("ORACLE_PASSWORD", oracle::getPassword);

        // Kafka configuration
        String[] kafkaBootstrap = kafka.getBootstrapServers().split(":");
        registry.add("KAFKA_HOST", () -> kafkaBootstrap[0]);
        registry.add("KAFKA_PORT", () -> kafkaBootstrap[1]);
    }

    @Value("${app.kafka.topic}")
    private String topicName;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.key-deserializer}")
    private String keyDeserializer;

    @Value("${spring.kafka.consumer.value-deserializer}")
    private String valueDeserializer;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Test
    @Transactional
    void testOutboxEventPublishedToKafka() throws Exception {
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType("TRANSACTION_CREATED")
                .aggregateId(123L)
                .payload("{\"transactionId\":\"txn-123\",\"amount\":100.50}")
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        OutboxEvent savedEvent = outboxEventRepository.save(outboxEvent);
        assertThat(savedEvent.getEventId()).isNotNull();
        assertThat(savedEvent.getStatus()).isEqualTo("PENDING");

        outboxEventPublisher.publishPendingEvents();

        OutboxEvent updatedEvent = outboxEventRepository.findById(savedEvent.getEventId()).orElseThrow();
        assertThat(updatedEvent.getPublishedAt()).isNotNull();

        assertKafkaMessageReceived("txn-123");
    }

    private void assertKafkaMessageReceived(String expectedContent) throws Exception {
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put("bootstrap.servers", bootstrapServers);
        consumerProps.put("group.id", "test-group");
        consumerProps.put("key.deserializer", keyDeserializer);
        consumerProps.put("value.deserializer", valueDeserializer);
        consumerProps.put("auto.offset.reset", "earliest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singletonList(topicName));

            ConsumerRecords<String, String> records = ConsumerRecords.empty();
            int attempts = 0;
            while (records.isEmpty() && attempts < 10) {
                records = consumer.poll(Duration.ofSeconds(1));
                attempts++;
            }

            assertThat(records).isNotEmpty();

            boolean messageFound = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.value().contains(expectedContent)) {
                    messageFound = true;
                    break;
                }
            }
            assertThat(messageFound).isTrue();
        }
    }
}
