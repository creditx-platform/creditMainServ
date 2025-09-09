package com.creditx.main;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Testcontainers
@JdbcTest
@ActiveProfiles("test")
class SchemaIntegrationTest {

  @SuppressWarnings("resource")
  @Container
  static final OracleContainer oracle = new OracleContainer(
      "gvenzl/oracle-free:latest-faststart").withUsername("testuser").withPassword("testpassword");
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", oracle::getJdbcUrl);
    registry.add("spring.datasource.username", oracle::getUsername);
    registry.add("spring.datasource.password", oracle::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
  }

  @Test
  void testFlywayAppliedSchema() {
    // Test that CMS_PROCESSED_EVENTS table exists
    Integer processedEventsTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_tables WHERE table_name = 'CMS_PROCESSED_EVENTS'",
        Integer.class);
    assertThat(processedEventsTableCount).isEqualTo(1);

    // Test that CMS_ACCOUNTS table exists
    Integer accountsTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_tables WHERE table_name = 'CMS_ACCOUNTS'", Integer.class);
    assertThat(accountsTableCount).isEqualTo(1);

    // Test that CMS_TRANSACTIONS table exists
    Integer transactionsTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_tables WHERE table_name = 'CMS_TRANSACTIONS'", Integer.class);
    assertThat(transactionsTableCount).isEqualTo(1);

    // Test that CMS_OUTBOX_EVENTS table exists
    Integer outboxEventsTableCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM user_tables WHERE table_name = 'CMS_OUTBOX_EVENTS'", Integer.class);
    assertThat(outboxEventsTableCount).isEqualTo(1);

    // Test inserting into CMS_PROCESSED_EVENTS
    jdbcTemplate.update("""
            INSERT INTO CMS_PROCESSED_EVENTS (EVENT_ID, PAYLOAD_HASH, STATUS, PROCESSED_AT)
            VALUES (?, ?, 'PROCESSED', SYSTIMESTAMP)
        """, "test-event-123", "hash123");

    Integer processedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM CMS_PROCESSED_EVENTS WHERE EVENT_ID = ?", Integer.class,
        "test-event-123");
    assertThat(processedCount).isEqualTo(1);

    // Test inserting into CMS_ACCOUNTS
    jdbcTemplate.update("""
            INSERT INTO CMS_ACCOUNTS (CUSTOMER_ID, ACCOUNT_TYPE, STATUS, AVAILABLE_BALANCE, RESERVED, CREDIT_LIMIT)
            VALUES (?, ?, ?, ?, ?, ?)
        """, 999L, "ISSUER", "ACTIVE", 1000.00, 0.00, 5000.00);

    Integer accountsCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM CMS_ACCOUNTS WHERE CUSTOMER_ID = ?", Integer.class, 999L);
    assertThat(accountsCount).isEqualTo(1);
  }
}