package com.cp.ecommerce.adapter.common.constant;

import lombok.experimental.UtilityClass;

/**
 * Class with validation constants.
 */
@UtilityClass
public class ValidationConstants {

    public final int ORDER_REMARKS_MAX = 800;

    public final int CONTACT_NAME_MAX = 80;

    public final int CONTACT_EMAIL_MAX = 255;

    public final int CONTACT_PHONE_MAX = 25;

    public final int ADDRESS_STREET_MAX = 35;

    public final int ADDRESS_POSTAL_CODE_MAX = 35;

    public final int ADDRESS_CITY_MAX = 300;

    public final String VALIDATION_FAILED = "Validation Failed: ";

    public final String INVALID_STREET = "Invalid Street Address";

    public final String INVALID_POSTAL_CODE = "Invalid Postal Code";

    public final String INVALID_CITY = "Invalid City";

    public final String INVALID_COUNTRY_CODE = "Invalid Country Code";

    public final String INVALID_FULL_NAME = "Invalid Full name";

    public final String INVALID_EMAIL = "Invalid Email";

    public final String INVALID_PHONE = "Invalid Phone Number";

    public final String INVALID_REMARKS = "Invalid Remarks";

    public final String INVALID_CUSTOMER = "Customer is required";

}
