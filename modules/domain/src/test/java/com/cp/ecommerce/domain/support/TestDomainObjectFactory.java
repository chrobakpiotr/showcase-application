package com.cp.ecommerce.domain.support;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.PaymentMethod;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.review.Review;
import com.cp.ecommerce.domain.review.ReviewStatus;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.wishlist.Wishlist;
import com.cp.ecommerce.domain.wishlist.WishlistItem;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Factory for valid domain test objects.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestDomainObjectFactory {

    public static final Date TEST_CREATED = new Date(1710000000000L);

    public static final Long TEST_CUSTOMER_ID = 1001L;

    public static final String TEST_ORDER_NUMBER = "ORD-1001";

    public static final String TEST_PRODUCT_SKU = "SKU-1001";

    public static final String TEST_CART_ID = "CART-1001";

    public static final String TEST_REVIEW_ID = "REVIEW-1001";

    public static final String TEST_WISHLIST_ID = "WISHLIST-1001";

    public static final String TEST_RETURN_NUMBER = "RETURN-1001";

    public static final String TEST_NOTIFICATION_ID = "NOTIF-1001";

    public static final String TEST_SHIPMENT_NUMBER = "SHIP-1001";

    public static final String TEST_TRACKING_NUMBER = "DHL-TRACK-1001";

    public static Order validOrder() {

        return Order.builder()
                .remarks("remark")
                .orderNumber(TEST_ORDER_NUMBER)
                .created(TEST_CREATED)
                .customer(validCustomer())
                .items(List.of(validOrderLineItem()))
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

    public static OrderLineItem validOrderLineItem() {

        return OrderLineItem.builder()
                .sku(TEST_PRODUCT_SKU)
                .productName("Wireless Mouse")
                .unitPrice(new BigDecimal("29.99"))
                .quantity(2)
                .build();
    }

    public static Customer validCustomer() {

        return Customer.builder().id(TEST_CUSTOMER_ID).contact(validContact()).address(validAddress()).build();
    }

    public static Contact validContact() {

        return Contact.builder().fullName("John Doe").email("john.doe@test.com").phone("+48 123 456 789").build();
    }

    public static Address validAddress() {

        return Address.builder().street("Main Street 1").postalCode("12-345").city("Warsaw").countryCode("PL").build();
    }

    public static Category validCategory() {

        return Category.builder().id(1L).name("Electronics").slug("electronics").build();
    }

    public static Product validProduct() {

        return Product.builder()
                .sku(TEST_PRODUCT_SKU)
                .name("Wireless Mouse")
                .description("A reliable wireless mouse.")
                .category(validCategory())
                .unitPrice(new BigDecimal("29.99"))
                .imageUrl("https://example.com/images/wireless-mouse.png")
                .active(true)
                .created(TEST_CREATED)
                .build();
    }

    public static StockLevel validStockLevel() {

        return StockLevel.builder().sku(TEST_PRODUCT_SKU).quantityOnHand(10).quantityReserved(2).version(0).build();
    }

    public static PaymentTransaction validPaymentTransaction() {

        return PaymentTransaction.builder()
                .orderNumber(TEST_ORDER_NUMBER)
                .amount(new BigDecimal("59.98"))
                .method(PaymentMethod.CARD)
                .status(PaymentStatus.CAPTURED)
                .gatewayReference("mock-gw-1234")
                .created(TEST_CREATED)
                .build();
    }

    public static CartLineItem validCartLineItem() {

        return CartLineItem.builder()
                .sku(TEST_PRODUCT_SKU)
                .productName("Wireless Headphones")
                .unitPrice(BigDecimal.valueOf(99.99))
                .quantity(2)
                .build();
    }

    public static Cart validCart() {

        return Cart.builder().cartId(TEST_CART_ID).items(List.of(validCartLineItem())).updated(TEST_CREATED).build();
    }

    public static WishlistItem validWishlistItem() {

        return WishlistItem.builder().sku(TEST_PRODUCT_SKU).productName("Wireless Headphones").addedDate(TEST_CREATED).build();
    }

    public static Wishlist validWishlist() {

        return Wishlist.builder()
                .wishlistId(TEST_WISHLIST_ID)
                .items(List.of(validWishlistItem()))
                .updated(TEST_CREATED)
                .build();
    }

    public static Review validReview() {

        return Review.builder()
                .reviewId(TEST_REVIEW_ID)
                .sku(TEST_PRODUCT_SKU)
                .authorName("Jane Smith")
                .rating(5)
                .comment("Great product, works as expected.")
                .status(ReviewStatus.PENDING)
                .created(TEST_CREATED)
                .build();
    }

    public static ReturnRequest validReturnRequest() {

        return ReturnRequest.builder()
                .returnNumber(TEST_RETURN_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .sku(TEST_PRODUCT_SKU)
                .quantity(1)
                .reason("Damaged on arrival.")
                .status(ReturnStatus.REQUESTED)
                .requestedDate(TEST_CREATED)
                .refundAmount(new BigDecimal("29.99"))
                .build();
    }

    public static ReturnRequest validApprovedReturnRequest() {

        return ReturnRequest.builder()
                .returnNumber(TEST_RETURN_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .sku(TEST_PRODUCT_SKU)
                .quantity(1)
                .reason("Damaged on arrival.")
                .status(ReturnStatus.APPROVED)
                .requestedDate(TEST_CREATED)
                .decidedDate(new Date(TEST_CREATED.getTime() + 60000))
                .refundAmount(new BigDecimal("29.99"))
                .build();
    }

    public static ReturnRequest validRejectedReturnRequest() {

        return ReturnRequest.builder()
                .returnNumber(TEST_RETURN_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .sku(TEST_PRODUCT_SKU)
                .quantity(1)
                .reason("Damaged on arrival.")
                .status(ReturnStatus.REJECTED)
                .requestedDate(TEST_CREATED)
                .decidedDate(new Date(TEST_CREATED.getTime() + 60000))
                .refundAmount(new BigDecimal("29.99"))
                .build();
    }

    public static ReturnRequest validRefundedReturnRequest() {

        return ReturnRequest.builder()
                .returnNumber(TEST_RETURN_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .sku(TEST_PRODUCT_SKU)
                .quantity(1)
                .reason("Damaged on arrival.")
                .status(ReturnStatus.REFUNDED)
                .requestedDate(TEST_CREATED)
                .decidedDate(new Date(TEST_CREATED.getTime() + 60000))
                .refundAmount(new BigDecimal("29.99"))
                .build();
    }

    public static Notification validNotification() {

        return Notification.builder()
                .notificationId(TEST_NOTIFICATION_ID)
                .recipientEmail("john.doe@test.com")
                .channel(NotificationChannel.EMAIL)
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Your order was confirmed.")
                .status(NotificationStatus.SENT)
                .createdDate(TEST_CREATED)
                .sentDate(new Date(TEST_CREATED.getTime() + 60000))
                .build();
    }

    public static Shipment validShipment() {

        return Shipment.builder()
                .shipmentNumber(TEST_SHIPMENT_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .carrier("DHL")
                .trackingNumber(TEST_TRACKING_NUMBER)
                .status(ShipmentStatus.PENDING)
                .createdDate(TEST_CREATED)
                .build();
    }

    public static Shipment validDispatchedShipment() {

        final Date dispatchedDate = new Date(TEST_CREATED.getTime() + 60000);
        return Shipment.builder()
                .shipmentNumber(TEST_SHIPMENT_NUMBER)
                .orderNumber(TEST_ORDER_NUMBER)
                .carrier("DHL")
                .trackingNumber(TEST_TRACKING_NUMBER)
                .status(ShipmentStatus.DISPATCHED)
                .dispatchedDate(dispatchedDate)
                .estimatedDeliveryDate(new Date(dispatchedDate.getTime() + 5 * 24 * 60 * 60 * 1000L))
                .createdDate(TEST_CREATED)
                .build();
    }

    public static Shipment validInTransitShipment() {

        final Shipment dispatchedShipment = validDispatchedShipment();
        return Shipment.builder()
                .shipmentNumber(dispatchedShipment.getShipmentNumber())
                .orderNumber(dispatchedShipment.getOrderNumber())
                .carrier(dispatchedShipment.getCarrier())
                .trackingNumber(dispatchedShipment.getTrackingNumber())
                .status(ShipmentStatus.IN_TRANSIT)
                .dispatchedDate(dispatchedShipment.getDispatchedDate())
                .estimatedDeliveryDate(dispatchedShipment.getEstimatedDeliveryDate())
                .createdDate(dispatchedShipment.getCreatedDate())
                .build();
    }

    public static Shipment validDeliveredShipment() {

        final Shipment inTransitShipment = validInTransitShipment();
        return Shipment.builder()
                .shipmentNumber(inTransitShipment.getShipmentNumber())
                .orderNumber(inTransitShipment.getOrderNumber())
                .carrier(inTransitShipment.getCarrier())
                .trackingNumber(inTransitShipment.getTrackingNumber())
                .status(ShipmentStatus.DELIVERED)
                .dispatchedDate(inTransitShipment.getDispatchedDate())
                .estimatedDeliveryDate(inTransitShipment.getEstimatedDeliveryDate())
                .deliveredDate(new Date(inTransitShipment.getEstimatedDeliveryDate().getTime()))
                .createdDate(inTransitShipment.getCreatedDate())
                .build();
    }

}
