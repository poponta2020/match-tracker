package com.karuta.matchtracker;

import com.karuta.matchtracker.config.TestContainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestContainersConfig.class)
class MatchTrackerApplicationTests {

	@Test
	void contextLoads() {
	}

}
