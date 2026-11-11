package com.cp.ecommerce.application;

import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Repeat-customer orders on the local H2 database. */
@ActiveProfiles("test-h2")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:repeat_customer_order;DB_CLOSE_DELAY=-1")
class RepeatCustomerOrderH2IntegrationTest extends AbstractRepeatCustomerOrderIntegrationTest {

    @MockitoBean
    private SaveNotificationOutPort saveNotificationOutPort;
}
