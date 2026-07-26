package com.cp.ecommerce.domain.customer;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Class representing customer's contact data.
 */
@Value
@Builder
@DomainObject
@EqualsAndHashCode(callSuper = false)
public class Contact extends ValidDomainObject<Contact> {

    private static final String PHONE_PATTERN = "^$|[- +()0-9]+";

    @Size(max = ValidationConstants.CONTACT_NAME_MAX, message = ValidationConstants.INVALID_FULL_NAME)
    String fullName;

    @Email(message = ValidationConstants.INVALID_EMAIL)
    @Size(max = ValidationConstants.CONTACT_EMAIL_MAX, message = ValidationConstants.INVALID_EMAIL)
    String email;

    @Pattern(regexp = PHONE_PATTERN, message = ValidationConstants.INVALID_PHONE)
    @Size(max = ValidationConstants.CONTACT_PHONE_MAX, message = ValidationConstants.INVALID_PHONE)
    String phone;

    public static Contact.ContactBuilder builder() {

        return new Contact.ContactBuilder() {

            @Override
            public Contact build() {

                return super.build().validate();
            }
        };
    }
}
