package com.cp.ecommerce.adapter.web.returns.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.web.returns.resource.RequestReturnResource;
import com.cp.ecommerce.adapter.web.returns.resource.ReturnRequestResource;
import com.cp.ecommerce.domain.returns.ReturnRequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the return mapper behavior.
 */
class ReturnWebMapperTest {

    private final transient ReturnWebMapper returnWebMapper = new ReturnWebMapper();

    @Test
    void shouldReturnEmptyIfNullWhileMapToDomainObject() {

        final Optional<ReturnRequest> result = returnWebMapper.mapToDomainObject(null);

        assertFalse(result.isPresent());
    }

    @Test
    void shouldMapRequestReturnResourceToDomainObject() {

        final RequestReturnResource resource = RequestReturnResource.builder()
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .sku(ReturnRequestBuilder.TEST_SKU)
                .quantity(ReturnRequestBuilder.TEST_QUANTITY)
                .reason(ReturnRequestBuilder.TEST_REASON)
                .build();

        final Optional<ReturnRequest> result = returnWebMapper.mapToDomainObject(resource);

        assertTrue(result.isPresent());
        assertThat(result.get().getOrderNumber()).isEqualTo(ReturnRequestBuilder.TEST_ORDER_NUMBER);
        assertThat(result.get().getRefundAmount()).isZero();
    }

    @Test
    void shouldDefaultQuantityToZeroWhenMappingRequestResourceWithNullQuantity() {

        final RequestReturnResource resource = RequestReturnResource.builder()
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .sku(ReturnRequestBuilder.TEST_SKU)
                .quantity(null)
                .reason(ReturnRequestBuilder.TEST_REASON)
                .build();

        final Optional<ReturnRequest> result = returnWebMapper.mapToDomainObject(resource);

        assertTrue(result.isPresent());
        assertThat(result.get().getQuantity()).isZero();
    }

    @Test
    void shouldReturnEmptyIfNullWhileMapToResource() {

        final Optional<ReturnRequestResource> resource = returnWebMapper.mapToResource(null);

        assertFalse(resource.isPresent());
    }

    @Test
    void shouldMapReturnRequestToResource() {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();

        final Optional<ReturnRequestResource> result = returnWebMapper.mapToResource(returnRequest);

        assertTrue(result.isPresent());
        assertThat(result.get().returnNumber()).isEqualTo(ReturnRequestBuilder.TEST_RETURN_NUMBER);
        assertThat(result.get().refundAmount()).isEqualByComparingTo(ReturnRequestBuilder.TEST_REFUND_AMOUNT);
        assertThat(result.get().status()).isEqualTo(ReturnRequestBuilder.TEST_STATUS.name());
    }

}
