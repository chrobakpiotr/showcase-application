package com.cp.ecommerce.foundation.constant;

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

    /** {@code "CART-"} prefix (5 chars) + a random {@link java.util.UUID} (36 chars) = 41 chars. */
    public final int CART_ID_MAX = 41;

    public final int CART_SKU_MAX = 40;

    public final int CART_PRODUCT_NAME_MAX = 200;

    public final int COUPON_CODE_MAX = 30;

    public final String INVALID_CART_ID = "Invalid Cart Id";

    public final String INVALID_CART_SKU = "Invalid SKU";

    public final String INVALID_CART_PRODUCT_NAME = "Invalid Product Name";

    public final String INVALID_CART_UNIT_PRICE = "Invalid Unit Price";

    public final String INVALID_CART_QUANTITY = "Quantity must be at least 1";

    public final String INVALID_CART_COUPON_CODE = "Invalid Coupon Code";

    public final String INVALID_CART_DISCOUNT_AMOUNT = "Invalid Discount Amount";

    /** {@code "REVIEW-"} prefix (7 chars) + a random {@link java.util.UUID} (36 chars) = 43 chars. */
    public final int REVIEW_ID_MAX = 43;

    public final int REVIEW_SKU_MAX = 40;

    public final int REVIEW_AUTHOR_NAME_MAX = 80;

    public final int REVIEW_COMMENT_MAX = 2000;

    public final String INVALID_REVIEW_ID = "Invalid Review Id";

    public final String INVALID_REVIEW_SKU = "Invalid SKU";

    public final String INVALID_REVIEW_AUTHOR_NAME = "Invalid Author Name";

    public final String INVALID_REVIEW_RATING = "Rating must be between 1 and 5";

    public final String INVALID_REVIEW_COMMENT = "Invalid Comment";

    public final String INVALID_REVIEW_STATUS = "Invalid Review Status";

    /** {@code "WISHLIST-"} prefix (9 chars) + a random {@link java.util.UUID} (36 chars) = 45 chars. */
    public final int WISHLIST_ID_MAX = 45;

    /** {@code "RETURN-"} prefix (7 chars) + a random {@link java.util.UUID} (36 chars) = 43 chars. */
    public final int RETURN_NUMBER_MAX = 43;

    /** {@code "NOTIF-"} prefix (6 chars) + a random {@link java.util.UUID} (36 chars) = 42 chars. */
    public final int NOTIFICATION_ID_MAX = 42;

    /** {@code "SHIP-"} prefix (5 chars) + a random {@link java.util.UUID} (36 chars) = 41 chars. */
    public final int SHIPMENT_NUMBER_MAX = 41;

    public final int RETURN_SKU_MAX = 40;

    public final int RETURN_REASON_MAX = 2000;

    public final int NOTIFICATION_SUBJECT_MAX = 255;

    public final int NOTIFICATION_BODY_MAX = 2000;

    public final int SHIPMENT_CARRIER_MAX = 80;

    public final int SHIPMENT_TRACKING_NUMBER_MAX = 60;

    public final int WISHLIST_SKU_MAX = 40;

    public final int WISHLIST_PRODUCT_NAME_MAX = 200;

    public final String INVALID_WISHLIST_ID = "Invalid Wishlist Id";

    public final String INVALID_WISHLIST_SKU = INVALID_CART_SKU;

    public final String INVALID_WISHLIST_PRODUCT_NAME = INVALID_CART_PRODUCT_NAME;

    public final String INVALID_WISHLIST_ADDED_DATE = "Added date is required";

    public final String INVALID_RETURN_NUMBER = "Invalid Return Number";

    public final String INVALID_RETURN_ORDER_NUMBER = "Invalid Order Number";

    public final String INVALID_RETURN_SKU = INVALID_CART_SKU;

    public final String INVALID_RETURN_QUANTITY = "Quantity must be at least 1";

    public final String INVALID_RETURN_REASON = "Invalid Return Reason";

    public final String INVALID_RETURN_STATUS = "Invalid Return Status";

    public final String INVALID_RETURN_REQUESTED_DATE = "Requested date is required";

    public final String INVALID_RETURN_REFUND_AMOUNT = "Invalid Refund Amount";

    public final String INVALID_NOTIFICATION_ID = "Invalid Notification Id";

    public final String INVALID_NOTIFICATION_RECIPIENT_EMAIL = "Invalid Recipient Email";

    public final String INVALID_NOTIFICATION_CHANNEL = "Invalid Notification Channel";

    public final String INVALID_NOTIFICATION_TYPE = "Invalid Notification Type";

    public final String INVALID_NOTIFICATION_SUBJECT = "Invalid Notification Subject";

    public final String INVALID_NOTIFICATION_BODY = "Invalid Notification Body";

    public final String INVALID_NOTIFICATION_STATUS = "Invalid Notification Status";

    public final String INVALID_NOTIFICATION_CREATED_DATE = "Created date is required";

    public final String INVALID_SHIPMENT_NUMBER = "Invalid Shipment Number";

    public final String INVALID_SHIPMENT_ORDER_NUMBER = "Invalid Order Number";

    public final String INVALID_SHIPMENT_CARRIER = "Invalid Carrier";

    public final String INVALID_SHIPMENT_TRACKING_NUMBER = "Invalid Tracking Number";

    public final String INVALID_SHIPMENT_STATUS = "Invalid Shipment Status";

    public final String INVALID_SHIPMENT_CREATED_DATE = "Created date is required";

    public final String INVALID_COUPON_CODE = "Invalid Coupon Code";

    public final String INVALID_COUPON_DISCOUNT_TYPE = "Invalid Discount Type";

    public final String INVALID_COUPON_DISCOUNT_VALUE = "Invalid Discount Value";

    public final String INVALID_COUPON_MINIMUM_ORDER_AMOUNT = "Invalid Minimum Order Amount";

    public final String INVALID_COUPON_MAX_REDEMPTIONS = "Invalid Maximum Redemptions";

    public final String INVALID_COUPON_REDEMPTION_COUNT = "Invalid Redemption Count";

    public final int ORDER_LINE_ITEM_SKU_MAX = 40;

    public final int ORDER_LINE_ITEM_PRODUCT_NAME_MAX = 200;

    public final String INVALID_ORDER_LINE_ITEM_SKU = INVALID_CART_SKU;

    public final String INVALID_ORDER_LINE_ITEM_PRODUCT_NAME = INVALID_CART_PRODUCT_NAME;

    public final String INVALID_ORDER_LINE_ITEM_UNIT_PRICE = INVALID_CART_UNIT_PRICE;

    public final String INVALID_ORDER_LINE_ITEM_QUANTITY = "Quantity must be at least 1";

    public final String INVALID_ORDER_LINE_ITEMS = "At least one line item is required";

    public final String INVALID_ORDER_COUPON_CODE = INVALID_COUPON_CODE;

    public final String INVALID_ORDER_DISCOUNT_AMOUNT = "Invalid Discount Amount";

    public final String INVALID_PAYMENT_METHOD = "Invalid Payment Method";

    public final int PAYMENT_GATEWAY_REFERENCE_MAX = 80;

    public final String INVALID_PAYMENT_ORDER_NUMBER = "Invalid Order Number";

    public final String INVALID_PAYMENT_AMOUNT = "Payment amount must not be negative";

    public final String INVALID_PAYMENT_GATEWAY_REFERENCE = "Invalid Gateway Reference";

}
