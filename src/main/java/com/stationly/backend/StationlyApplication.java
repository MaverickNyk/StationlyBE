package com.stationly.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StationlyApplication {

	public static void main(String[] args) {
		SpringApplication.run(StationlyApplication.class, args);
	}

}
