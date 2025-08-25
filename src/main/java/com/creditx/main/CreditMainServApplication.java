package com.creditx.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CreditMainServApplication {

	public static void main(String[] args) {
		SpringApplication.run(CreditMainServApplication.class, args);
	}

}
