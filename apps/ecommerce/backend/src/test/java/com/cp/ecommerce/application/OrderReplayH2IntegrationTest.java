package com.cp.ecommerce.application;

import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Same transaction contract on the local database. */
@ActiveProfiles("test-h2")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:order_replay;DB_CLOSE_DELAY=-1")
class OrderReplayH2IntegrationTest extends AbstractOrderReplayIntegrationTest {

    @MockitoBean
    private SaveNotificationOutPort saveNotificationOutPort;
}
