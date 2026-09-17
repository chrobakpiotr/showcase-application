package com.cp.ecommerce.application;

import org.springframework.test.context.ActiveProfiles;

/**
 * Test class that checks whether spring context of the app has started properly with H2 database.
 */
@ActiveProfiles("test-h2")
class EcommerceApplicationWithInMemoryH2DbTest extends AbstractEcommerceApplicationTest {

}
