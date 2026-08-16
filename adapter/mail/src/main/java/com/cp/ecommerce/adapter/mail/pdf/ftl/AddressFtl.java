package com.cp.ecommerce.adapter.mail.pdf.ftl;

import java.io.Serializable;

import com.cp.ecommerce.domain.customer.Address;

import lombok.Builder;

/**
 * FTL mapper used for mapping {@link Address} and {@link Address} objects into theirs FTL counterparts.
 */
@Builder
public record AddressFtl(String countryCode, String postalCode, String city, String street) implements Serializable {

    private static final long serialVersionUID = 1L;

    public static AddressFtl of(final Address address) {

        if (address == null) {
            return null;
        }

        return AddressFtl.builder()
                .street(address.getStreet())
                .countryCode(address.getCountryCode())
                .postalCode(address.getPostalCode())
                .city(address.getCity())
                .build();
    }

}
