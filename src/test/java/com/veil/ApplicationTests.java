package com.veil;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "veil.connections.file=target/test-connections.json")
class ApplicationTests {

	@Test
	void contextLoads() {
	}

}
