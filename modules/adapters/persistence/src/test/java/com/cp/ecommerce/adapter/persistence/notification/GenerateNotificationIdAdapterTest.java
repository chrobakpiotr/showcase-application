package com.cp.ecommerce.adapter.persistence.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link GenerateNotificationIdAdapter}.
 */
class GenerateNotificationIdAdapterTest {

    private final transient GenerateNotificationIdAdapter generateNotificationIdAdapter = new GenerateNotificationIdAdapter();

    @Test
    void shouldGenerateNotificationIdWithExpectedPrefix() {

        assertTrue(generateNotificationIdAdapter.generate().startsWith("NOTIF-"));
    }

    @Test
    void shouldGenerateUniqueNotificationIdOnEachCall() {

        assertNotEquals(generateNotificationIdAdapter.generate(), generateNotificationIdAdapter.generate());
    }

}
