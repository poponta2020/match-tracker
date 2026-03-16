package com.karuta.matchtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MatchTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatchTrackerApplication.class, args);
	}

}
