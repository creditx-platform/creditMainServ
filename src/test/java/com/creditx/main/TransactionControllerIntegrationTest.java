package com.creditx.main;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import org.springframework.http.ResponseEntity;

import com.creditx.main.dto.CreateTransactionResponse;
import com.creditx.main.model.OutboxEvent;
import com.creditx.main.model.OutboxEventStatus;
import com.creditx.main.repository.OutboxEventRepository;
import com.creditx.main.repository.TransactionRepository;
import com.creditx.main.scheduler.OutboxEventPublishingScheduler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestChannelBinderConfiguration.class)
public class TransactionControllerIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:latest-faststart")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
    }

    @MockitoBean(name = "restTemplate")
    private RestTemplate mockedCreditHoldServiceClient;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testHttpClient;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxEventPublishingScheduler outboxEventPublisher;

    @Autowired
    private OutputDestination outputDestination;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Clear any existing messages from previous tests
        while (outputDestination.receive(100, "transactions-out-0") != null) {
            // Drain the output destination
        }

        // Clear database for clean test state
        outboxEventRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    // Utility to stub RestTemplate for a successful hold response
    private void stubSuccessfulHoldResponse(Long holdId) {
        com.creditx.main.dto.CreateHoldResponse holdResponse = com.creditx.main.dto.CreateHoldResponse.builder()
                .holdId(holdId)
                .build();
        given(mockedCreditHoldServiceClient.postForEntity(anyString(), any(HttpEntity.class),
                eq(com.creditx.main.dto.CreateHoldResponse.class)))
                .willReturn(org.springframework.http.ResponseEntity.ok(holdResponse));
    }

    // Receive messages until a message with the expected transactionId is found or timeout elapses
    private Message<byte[]> receiveMessageForTransaction(Long expectedTxnId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Message<byte[]> msg = outputDestination.receive(500, "transactions-out-0");
            if (msg == null)
                continue;
            try {
                JsonNode node = objectMapper.readTree(new String(msg.getPayload()));
                if (node.has("transactionId") && node.get("transactionId").asLong() == expectedTxnId) {
                    return msg;
                }
            } catch (Exception e) {
                // ignore and continue
            }
        }
        return null;
    }

    @Test
    @Transactional
    void testCreateTransactionEndToEndFlow() throws Exception {
        // Given: Valid transaction request
        String requestBody = """
                {
                    "issuerAccountId": 1,
                    "merchantAccountId": 2,
                    "amount": 150.75,
                    "currency": "USD"
                }
                """;

        // Stub external hold service to return a hold id
        stubSuccessfulHoldResponse(123L);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When: POST request is made to create transaction
    ResponseEntity<CreateTransactionResponse> response = testHttpClient.exchange(
        "/api/transactions",
                HttpMethod.POST,
                request,
                CreateTransactionResponse.class);

        // Then: Response should be successful
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        CreateTransactionResponse responseBody = response.getBody();
        assertThat(responseBody).isNotNull();

        Long transactionId = Objects.requireNonNull(responseBody).getTransactionId();
        assertThat(transactionId).isNotNull();
        assertThat(responseBody.getStatus().toString()).isEqualTo("PENDING");

        // Verify transaction was persisted
        var savedTransaction = transactionRepository.findById(transactionId);
        assertThat(savedTransaction).isPresent();
        assertThat(savedTransaction.get().getAmount()).isEqualByComparingTo("150.75");
        assertThat(savedTransaction.get().getCurrency()).isEqualTo("USD");
        assertThat(savedTransaction.get().getMerchantId()).isEqualTo(2L);

        // Verify outbox event was created
        var outboxEvents = outboxEventRepository.findAll()
                .stream()
                .filter(e -> e.getAggregateId().equals(transactionId))
                .toList();
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent outboxEvent = outboxEvents.get(0);
        assertThat(outboxEvent.getEventType()).isEqualTo("transaction.initiated");
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getPayload()).contains("\"transactionId\":" + transactionId);
        assertThat(outboxEvent.getPayload()).contains("\"issuerAccountId\":1");
        assertThat(outboxEvent.getPayload()).contains("\"merchantAccountId\":2");
        assertThat(outboxEvent.getPayload()).contains("\"amount\":150.75");

        // When: Outbox publisher processes pending events
        outboxEventPublisher.publishPendingEvents();

        // Then: Event should be marked as published
        var updatedEvent = outboxEventRepository.findById(outboxEvent.getEventId()).orElseThrow();
        assertThat(updatedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(updatedEvent.getPublishedAt()).isNotNull();

        // And: Message should be sent to stream (find the message for our transaction)
        Message<byte[]> streamMessage = receiveMessageForTransaction(transactionId, 2000);
        assertThat(streamMessage).isNotNull();

        String messagePayload = new String(streamMessage.getPayload());
        JsonNode messageNode = objectMapper.readTree(messagePayload);

        assertThat(messageNode.get("transactionId").asLong()).isEqualTo(transactionId);
        assertThat(messageNode.get("issuerAccountId").asLong()).isEqualTo(1L);
        assertThat(messageNode.get("merchantAccountId").asLong()).isEqualTo(2L);
        assertThat(messageNode.get("amount").asDouble()).isEqualTo(150.75);
        assertThat(messageNode.get("currency").asText()).isEqualTo("USD");

        // Verify message headers
        assertThat(streamMessage.getHeaders().get("key")).isEqualTo(transactionId.toString());
    }

    @Test
    @Transactional
    void testCreateTransactionWithInvalidAccount() throws Exception {
        // Given: Request with non-existent issuer account
        String requestBody = """
                {
                    "issuerAccountId": 999,
                    "merchantAccountId": 2,
                    "amount": 100.00,
                    "currency": "USD"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When & Then: Should return error and no events should be created
    ResponseEntity<String> response = testHttpClient.exchange(
        "/api/transactions",
                HttpMethod.POST,
                request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Verify no outbox events were created
        var outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).isEmpty();

        // Verify no stream messages were sent
        Message<byte[]> streamMessage = outputDestination.receive(500, "transactions-out-0");
        assertThat(streamMessage).isNull();
    }

    @Test
    @Transactional
    void testCreateTransactionWithInactiveAccount() throws Exception {
        // Given: Request with blocked issuer account (account ID 5)
        String requestBody = """
                {
                    "issuerAccountId": 5,
                    "merchantAccountId": 2,
                    "amount": 100.00,
                    "currency": "USD"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

    // When & Then: Should return error for inactive account
    ResponseEntity<String> response = testHttpClient.exchange(
        "/api/transactions",
                HttpMethod.POST,
                request,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // Verify no outbox events were created
        var outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).isEmpty();

        // Verify no stream messages were sent
        Message<byte[]> streamMessage = outputDestination.receive(500, "transactions-out-0");
        assertThat(streamMessage).isNull();
    }

    @Test
    @Transactional
    void testMultipleTransactionsCreateMultipleEvents() throws Exception {
        // Given: Multiple valid transaction requests
        String request1 = """
                {
                    "issuerAccountId": 1,
                    "merchantAccountId": 2,
                    "amount": 50.00,
                    "currency": "USD"
                }
                """;

        String request2 = """
                {
                    "issuerAccountId": 3,
                    "merchantAccountId": 2,
                    "amount": 75.25,
                    "currency": "USD"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Stub external hold service for both transactions
        stubSuccessfulHoldResponse(201L);
        stubSuccessfulHoldResponse(202L);

    // When: Both transactions are created
    ResponseEntity<CreateTransactionResponse> response1 = testHttpClient.exchange(
        "/api/transactions",
                HttpMethod.POST,
                new HttpEntity<>(request1, headers),
                CreateTransactionResponse.class);

    ResponseEntity<CreateTransactionResponse> response2 = testHttpClient.exchange(
        "/api/transactions",
                HttpMethod.POST,
                new HttpEntity<>(request2, headers),
                CreateTransactionResponse.class);

        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Extract transaction IDs
        assertThat(response1.getBody()).isNotNull();
        assertThat(response2.getBody()).isNotNull();

        Long txnId1 = Objects.requireNonNull(response1.getBody()).getTransactionId();
        Long txnId2 = Objects.requireNonNull(response2.getBody()).getTransactionId();

        // Verify outbox events were created for both transactions
        var outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(2);

        // When: Publisher processes all pending events
        outboxEventPublisher.publishPendingEvents();

        // Then: Both events should be published to stream (find messages for both txnIDs)
        Message<byte[]> message1 = receiveMessageForTransaction(txnId1, 2000);
        Message<byte[]> message2 = receiveMessageForTransaction(txnId2, 2000);

        assertThat(message1).isNotNull();
        assertThat(message2).isNotNull();

        // Verify both transaction IDs are present in the messages
        String payload1 = new String(message1.getPayload());
        String payload2 = new String(message2.getPayload());

        boolean containsTxn1 = payload1.contains("\"transactionId\":" + txnId1) ||
                payload2.contains("\"transactionId\":" + txnId1);
        boolean containsTxn2 = payload1.contains("\"transactionId\":" + txnId2) ||
                payload2.contains("\"transactionId\":" + txnId2);

        assertThat(containsTxn1).isTrue();
        assertThat(containsTxn2).isTrue();
    }
}