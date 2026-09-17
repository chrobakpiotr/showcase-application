package com.cp.ecommerce.domain.catalog;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Representation of a catalog product domain object - the foundational bounded context every other planned ecommerce capability
 * (inventory, shopping cart, order line items, reviews) will reference by {@link #sku}.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Product extends ValidDomainObject<Product> {

    // Business identifier (analogous to Order.orderNumber): blank until ManageProductUseCase assigns one via
    // GenerateSkuOutPort on creation, present on every product returned from persistence.
    @Size(max = ValidationConstants.PRODUCT_SKU_MAX, message = ValidationConstants.INVALID_PRODUCT_NAME)
    String sku;

    @NotBlank(message = ValidationConstants.INVALID_PRODUCT_NAME)
    @Size(max = ValidationConstants.PRODUCT_NAME_MAX, message = ValidationConstants.INVALID_PRODUCT_NAME)
    String name;

    @Size(max = ValidationConstants.PRODUCT_DESCRIPTION_MAX, message = ValidationConstants.INVALID_PRODUCT_DESCRIPTION)
    String description;

    @NotNull(message = ValidationConstants.INVALID_PRODUCT_CATEGORY)
    @Valid
    Category category;

    @NotNull(message = ValidationConstants.INVALID_PRODUCT_PRICE)
    @DecimalMin(value = "0.01", message = ValidationConstants.INVALID_PRODUCT_PRICE)
    BigDecimal unitPrice;

    @Size(max = ValidationConstants.PRODUCT_IMAGE_URL_MAX, message = ValidationConstants.INVALID_PRODUCT_IMAGE_URL)
    String imageUrl;

    @Builder.Default
    boolean active = true;

    Date created;

    public static Product.ProductBuilder builder() {

        return new Product.ProductBuilder() {

            @Override
            public Product build() {

                return super.build().validate();
            }
        };
    }

}
