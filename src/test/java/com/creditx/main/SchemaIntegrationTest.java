package com.creditx.main;

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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@ActiveProfiles("test")
public class SchemaIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static final OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:latest-faststart")
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
                .queryForObject("SELECT COUNT(*) FROM user_tables WHERE table_name = 'CMS_ACCOUNTS'", Integer.class);

        assertThat(tableCount).isEqualTo(1);

        jdbcTemplate.update("""
                    INSERT INTO CMS_ACCOUNTS (CUSTOMER_ID, ACCOUNT_TYPE, STATUS, AVAILABLE_BALANCE, RESERVED, CREDIT_LIMIT)
                    VALUES (? , 'ISSUER', 'ACTIVE', 5000, 0, 5000)
                """, 12345L);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CMS_ACCOUNTS WHERE CUSTOMER_ID = ?",
                Integer.class, 12345L);

        assertThat(count).isEqualTo(1);
    }
}