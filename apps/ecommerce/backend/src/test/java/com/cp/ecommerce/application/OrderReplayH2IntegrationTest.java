package com.cp.ecommerce.application;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Same transaction contract on the local database. */
@ActiveProfiles("test-h2")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:order_replay;DB_CLOSE_DELAY=-1")
class OrderReplayH2IntegrationTest extends AbstractOrderReplayIntegrationTest {
}
