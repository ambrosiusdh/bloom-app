package com.bloom.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.bloom.app"})
public class BloomAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BloomAppApplication.class, args);
	}

}
