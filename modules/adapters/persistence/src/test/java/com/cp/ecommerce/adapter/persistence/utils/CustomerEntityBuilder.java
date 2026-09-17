package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.customer.entity.CustomerEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link CustomerEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomerEntityBuilder {

    public static CustomerEntity mockContactEntity() {
        return CustomerEntity.builder().fullName("test test").email("test@test.com").phone("555 555 555").build();
    }

}
