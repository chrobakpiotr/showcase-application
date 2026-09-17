package com.cp.ecommerce.adapter.persistence.customer.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.persistence.customer.entity.CustomerEntity;
import com.cp.ecommerce.adapter.persistence.utils.CustomerEntityBuilder;
import com.cp.ecommerce.domain.customer.Customer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CustomerPersistenceMapper} test.
 */
@ExtendWith(MockitoExtension.class)
class CustomerPersistenceMapperTest {

    @InjectMocks
    private transient CustomerPersistenceMapper customerPersistenceMapper;

    @Test
    void shouldMapDomainToEntity() {

        final Customer customer = CustomerBuilder.mockCustomer();
        final Optional<CustomerEntity> result = customerPersistenceMapper.mapToEntity(customer);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isNull();
        assertThat(result.get().getEmail()).isNotBlank();
        assertThat(result.get().getCity()).isNotBlank();
        assertThat(result.get().getCountryCode()).isNotBlank();
        assertThat(result.get().getPostalCode()).isNotBlank();
        assertThat(result.get().getStreet()).isNotBlank();
        assertThat(result.get().getFullName()).isNotBlank();
        assertThat(result.get().getPhone()).isNotBlank();
    }

    @Test
    void shouldMapEntityToDomain() {

        final CustomerEntity entity = CustomerEntityBuilder.mockContactEntity();
        final Optional<Customer> result = customerPersistenceMapper.mapToDomainObject(entity);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isNull();
        assertThat(result.get().getAddress()).isNotNull();
        assertThat(result.get().getContact()).isNotNull();
    }

    @Test
    void shouldPropagatePersistedIdWhenMappingEntityToDomain() {

        final CustomerEntity entity = CustomerEntityBuilder.mockContactEntity();
        entity.setId(42L);
        final Optional<Customer> result = customerPersistenceMapper.mapToDomainObject(entity);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(42L);
    }

    @Test
    void shouldMapToDomainObject() {

        final Optional<Customer> obj = customerPersistenceMapper.mapToDomainObject(null);
        assertTrue(obj.isEmpty());
    }

}
