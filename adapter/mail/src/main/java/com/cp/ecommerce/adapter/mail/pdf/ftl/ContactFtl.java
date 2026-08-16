package com.cp.ecommerce.adapter.mail.pdf.ftl;

import java.io.Serializable;
import java.util.Optional;

import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;

import lombok.Builder;

import static java.util.Optional.ofNullable;

/**
 * FTL mapper used for mapping {@link Customer} objects into theirs FTL counterparts.
 */
@Builder
public record ContactFtl(String fullName, EmailFtl email, String phone, AddressFtl address, String firstName,
        String lastName) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static ContactFtl of(final Customer contact) {

        return contactFtlOf(contact);
    }

    private static ContactFtl contactFtlOf(final Customer addressInfo) {

        if (addressInfo == null) {
            return null;
        }

        final Optional<Address> address = ofNullable(addressInfo.getAddress());
        final Optional<Contact> contact = ofNullable(addressInfo.getContact());

        return ContactFtl.builder()
                .fullName(contact.map(Contact::getFullName).orElse(null))
                .email(EmailFtl.of(contact.map(Contact::getEmail).orElse(null)))
                .phone(contact.map(Contact::getPhone).orElse(null))
                .address(AddressFtl.of(address.orElse(null)))
                .build();
    }

}
