package com.cp.ecommerce.adapter.persistence.customer.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.customer.entity.CustomerEntity;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for changing {@link CustomerEntity} object into/from domain object.
 */
@Component
public class CustomerPersistenceMapper implements PersistenceMapper<Customer, CustomerEntity> {

    @Override
    public Optional<Customer> mapToDomainObject(final CustomerEntity contactEntity) {

        return Optional.ofNullable(contactEntity)
                .map(
                        entity -> Customer.builder()
                                .id(entity.getId())
                                .contact(
                                        Contact.builder()
                                                .fullName(entity.getFullName())
                                                .email(entity.getEmail())
                                                .phone(entity.getPhone())
                                                .build())
                                .address(
                                        Address.builder()
                                                .street(entity.getStreet())
                                                .postalCode(entity.getPostalCode())
                                                .city(entity.getCity())
                                                .countryCode(entity.getCountryCode())
                                                .build())
                                .build());
    }

    @Override
    public Optional<CustomerEntity> mapToEntity(final Customer domainObject) {

        final Optional<Contact> contact = Optional.ofNullable(domainObject.getContact());
        final Optional<Address> address = Optional.ofNullable(domainObject.getAddress());

        return Optional.of(
                CustomerEntity.builder()
                        .fullName(contact.map(Contact::getFullName).orElse(null))
                        .email(contact.map(Contact::getEmail).orElse(null))
                        .phone(contact.map(Contact::getPhone).orElse(null))
                        .street(address.map(Address::getStreet).orElse(null))
                        .postalCode(address.map(Address::getPostalCode).orElse(null))
                        .city(address.map(Address::getCity).orElse(null))
                        .countryCode(address.map(Address::getCountryCode).orElse(null))
                        .build());
    }
}
