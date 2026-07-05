package com.cooperativa.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry
@EnableScheduling
@EnableJpaAuditing
public class BackendCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendCoreApplication.class, args);
	}

}
