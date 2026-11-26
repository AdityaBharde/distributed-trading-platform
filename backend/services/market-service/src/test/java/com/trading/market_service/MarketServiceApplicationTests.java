package com.trading.market_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MarketServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
