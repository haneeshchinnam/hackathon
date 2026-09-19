package com.example.detection;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="AML_INTEGRATION_TESTS", matches="true")
@SpringBootTest
class DetectionApplicationTests {

	@Test
	void contextLoads() {
	}

}
