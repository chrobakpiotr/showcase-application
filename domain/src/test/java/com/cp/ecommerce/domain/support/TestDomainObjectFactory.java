package com.cp.ecommerce.domain.support;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.order.Order;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Factory for valid domain test objects.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestDomainObjectFactory {

    public static final Date TEST_CREATED = new Date(1710000000000L);

    public static final Long TEST_CUSTOMER_ID = 1001L;

    public static final String TEST_ORDER_NUMBER = "ORD-1001";

    public static final String TEST_PRODUCT_SKU = "SKU-1001";

    public static Order validOrder() {

        return Order.builder()
                .remarks("remark")
                .orderNumber(TEST_ORDER_NUMBER)
                .created(TEST_CREATED)
                .customer(validCustomer())
                .build();
    }

    public static Customer validCustomer() {

        return Customer.builder().id(TEST_CUSTOMER_ID).contact(validContact()).address(validAddress()).build();
    }

    public static Contact validContact() {

        return Contact.builder().fullName("John Doe").email("john.doe@test.com").phone("+48 123 456 789").build();
    }

    public static Address validAddress() {

        return Address.builder().street("Main Street 1").postalCode("12-345").city("Warsaw").countryCode("PL").build();
    }

    public static Category validCategory() {

        return Category.builder().id(1L).name("Electronics").slug("electronics").build();
    }

    public static Product validProduct() {

        return Product.builder()
                .sku(TEST_PRODUCT_SKU)
                .name("Wireless Mouse")
                .description("A reliable wireless mouse.")
                .category(validCategory())
                .unitPrice(new BigDecimal("29.99"))
                .imageUrl("https://example.com/images/wireless-mouse.png")
                .active(true)
                .created(TEST_CREATED)
                .build();
    }

    public static StockLevel validStockLevel() {

        return StockLevel.builder().sku(TEST_PRODUCT_SKU).quantityOnHand(10).quantityReserved(2).version(0).build();
    }

}
