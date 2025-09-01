package com.creditx.main;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Import(TestChannelBinderConfiguration.class)
class CreditMainServApplicationTests {

	@SuppressWarnings("resource")
	@Container
	static OracleContainer oracle = new OracleContainer("gvenzl/oracle-free:latest-faststart")
			.withDatabaseName("testdb")
			.withUsername("testuser")
			.withPassword("testpass");

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", oracle::getJdbcUrl);
		registry.add("spring.datasource.username", oracle::getUsername);
		registry.add("spring.datasource.password", oracle::getPassword);
	}

	@Test
	void contextLoads() {
	}

}
