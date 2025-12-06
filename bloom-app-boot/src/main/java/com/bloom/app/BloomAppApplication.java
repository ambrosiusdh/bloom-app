package com.bloom.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.bloom.app"})
public class BloomAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BloomAppApplication.class, args);
	}

}
