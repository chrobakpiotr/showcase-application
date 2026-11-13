package com.cp.ecommerce.adapter.mail.pdf.ftl;

import java.io.Serializable;

import com.cp.ecommerce.adapter.mail.pdf.utils.XmlText;
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
                .street(XmlText.clean(address.getStreet()))
                .countryCode(XmlText.clean(address.getCountryCode()))
                .postalCode(XmlText.clean(address.getPostalCode()))
                .city(XmlText.clean(address.getCity()))
                .build();
    }

    /** Omits the components, which hold personal data; templates read the components, never this text. */
    @Override
    public String toString() {

        return "AddressFtl[personal data omitted]";
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class AddressFtlBuilder {

        @Override
        public String toString() {

            return "AddressFtlBuilder[personal data omitted]";
        }
    }
}
