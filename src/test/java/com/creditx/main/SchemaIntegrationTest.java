package com.creditx.main;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@JdbcTest
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.baseline-on-migrate=true"
})
public class SchemaIntegrationTest {

    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
            .withUsername("testuser")
            .withPassword("testpassword");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", oracle::getJdbcUrl);
        registry.add("spring.datasource.username", oracle::getUsername);
        registry.add("spring.datasource.password", oracle::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "oracle.jdbc.OracleDriver");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testFlywayAppliedSchema() {
        Integer tableCount = jdbcTemplate
                .queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name = 'MAIN_ACCOUNTS'", Integer.class);

        assertThat(tableCount).isEqualTo(1);

        jdbcTemplate.update("""
                    INSERT INTO MAIN_ACCOUNTS (ACCOUNT_ID, CUSTOMER_ID, STATUS, AVAILABLE_BALANCE, CREDIT_LIMIT)
                    VALUES (MAIN_ACCT_SEQ.NEXTVAL, ?, 'ACTIVE', 500.00, 1000.00)
                """, 12345L);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM MAIN_ACCOUNTS WHERE CUSTOMER_ID = ?",
                Integer.class, 12345L);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void testTransactionConstraints() {
        assertThatThrownBy(() -> {
            jdbcTemplate.update("""
                        INSERT INTO MAIN_TRANSACTIONS (TRANSACTION_ID, ACCOUNT_ID, AMOUNT, CURRENCY, STATUS)
                        VALUES (MAIN_TXN_SEQ.NEXTVAL, 1, 100.00, 'USD', 'INVALID_STATUS')
                    """);
        }).hasMessageContaining("check constraint");
    }
}