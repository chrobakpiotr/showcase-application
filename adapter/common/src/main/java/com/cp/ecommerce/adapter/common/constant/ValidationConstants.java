package com.cp.ecommerce.adapter.common.constant;

import lombok.experimental.UtilityClass;

/**
 * Class with validation constants.
 */
@UtilityClass
public class ValidationConstants {

    public final int ORDER_REMARKS_MAX = 800;

    public final int SUPPORT_QUESTION_MAX = 2000;

    public final int ANALYTICS_QUESTION_MAX = 2000;

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

    public final String INVALID_SUPPORT_QUESTION = "Invalid Question";

    public final String INVALID_ANALYTICS_QUESTION = "Invalid Question";

    public final String INVALID_CUSTOMER = "Customer is required";

    public final int CATEGORY_NAME_MAX = 120;

    public final int CATEGORY_SLUG_MAX = 120;

    public final int PRODUCT_SKU_MAX = 40;

    public final int PRODUCT_NAME_MAX = 200;

    public final int PRODUCT_DESCRIPTION_MAX = 2000;

    public final int PRODUCT_IMAGE_URL_MAX = 500;

    public final String INVALID_CATEGORY_NAME = "Invalid Category Name";

    public final String INVALID_CATEGORY_SLUG = "Invalid Category Slug";

    public final String INVALID_PRODUCT_NAME = "Invalid Product Name";

    public final String INVALID_PRODUCT_DESCRIPTION = "Invalid Product Description";

    public final String INVALID_PRODUCT_PRICE = "Invalid Product Price";

    public final String INVALID_PRODUCT_IMAGE_URL = "Invalid Product Image URL";

    public final String INVALID_PRODUCT_CATEGORY = "Category is required";

    public final int INVENTORY_SKU_MAX = 40;

    public final String INVALID_INVENTORY_SKU = "Invalid SKU";

    public final String INVALID_INVENTORY_QUANTITY = "Quantity must not be negative";

    public final int CART_ID_MAX = 40;

    public final int CART_SKU_MAX = 40;

    public final int CART_PRODUCT_NAME_MAX = 200;

    public final String INVALID_CART_ID = "Invalid Cart Id";

    public final String INVALID_CART_SKU = "Invalid SKU";

    public final String INVALID_CART_PRODUCT_NAME = "Invalid Product Name";

    public final String INVALID_CART_UNIT_PRICE = "Invalid Unit Price";

    public final String INVALID_CART_QUANTITY = "Quantity must be at least 1";

    public final int REVIEW_ID_MAX = 40;

    public final int REVIEW_SKU_MAX = 40;

    public final int REVIEW_AUTHOR_NAME_MAX = 80;

    public final int REVIEW_COMMENT_MAX = 2000;

    public final String INVALID_REVIEW_ID = "Invalid Review Id";

    public final String INVALID_REVIEW_SKU = "Invalid SKU";

    public final String INVALID_REVIEW_AUTHOR_NAME = "Invalid Author Name";

    public final String INVALID_REVIEW_RATING = "Rating must be between 1 and 5";

    public final String INVALID_REVIEW_COMMENT = "Invalid Comment";

    public final String INVALID_REVIEW_STATUS = "Invalid Review Status";

}
