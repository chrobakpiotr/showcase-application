package com.cp.ecommerce.domain.customer;

import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.Valid;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Class representing customer domain object.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@Builder
public class Customer extends ValidDomainObject<Customer> {

    // Deliberately unconstrained: null for a customer freshly submitted with an order (no persistence identity yet),
    // populated only once CustomerPersistenceMapper reads an already-persisted CustomerEntity back from the database.
    Long id;

    @Valid
    Contact contact;

    @Valid
    Address address;

    public static CustomerBuilder builder() {

        return new CustomerBuilder() {

            @Override
            public Customer build() {

                return super.build().validate();
            }
        };
    }

}
