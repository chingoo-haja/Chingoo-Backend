package com.ldsilver.chingoohaja;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ChingooHajaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChingooHajaApplication.class, args);
	}

}
