package com.bloom.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BloomAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BloomAppApplication.class, args);
	}

}
