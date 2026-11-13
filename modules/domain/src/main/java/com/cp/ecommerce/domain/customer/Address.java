package com.cp.ecommerce.domain.customer;

import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Class representing customer's address data.
 */
@Value
@EqualsAndHashCode(callSuper = false)
@Builder
public class Address extends ValidDomainObject<Address> {

    @NotBlank(message = ValidationConstants.INVALID_STREET)
    @Size(max = ValidationConstants.ADDRESS_STREET_MAX, message = ValidationConstants.INVALID_STREET)
    @ToString.Exclude
    String street;

    @Size(max = ValidationConstants.ADDRESS_POSTAL_CODE_MAX, message = ValidationConstants.INVALID_POSTAL_CODE)
    @ToString.Exclude
    String postalCode;

    @NotBlank(message = ValidationConstants.INVALID_CITY)
    @Size(max = ValidationConstants.ADDRESS_CITY_MAX, message = ValidationConstants.INVALID_CITY)
    @ToString.Exclude
    String city;

    @NotBlank(message = ValidationConstants.INVALID_COUNTRY_CODE)
    String countryCode;

    public static AddressBuilder builder() {

        return new AddressBuilder() {

            @Override
            public Address build() {

                return super.build().validate();
            }
        };
    }

    /** Lombok completes this builder; only its text is written here, because a builder holds personal data too. */
    public static class AddressBuilder {

        @Override
        public String toString() {

            return "AddressBuilder[personal data omitted]";
        }
    }
}
