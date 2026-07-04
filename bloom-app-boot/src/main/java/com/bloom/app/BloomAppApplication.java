package com.bloom.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.bloom.app"})
@ConfigurationPropertiesScan
public class BloomAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BloomAppApplication.class, args);
	}

}
