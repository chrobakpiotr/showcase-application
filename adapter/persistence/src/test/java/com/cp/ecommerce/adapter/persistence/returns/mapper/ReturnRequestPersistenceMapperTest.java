package com.cp.ecommerce.adapter.persistence.returns.mapper;

import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.persistence.utils.ReturnRequestEntityBuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link ReturnRequestPersistenceMapper}.
 */
class ReturnRequestPersistenceMapperTest {

    private final transient ReturnRequestPersistenceMapper returnRequestPersistenceMapper = new ReturnRequestPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final var returnRequest = ReturnRequestBuilder.mockReturnRequest();

        final var result = returnRequestPersistenceMapper.mapToEntity(returnRequest);

        assertTrue(result.isPresent());
        assertEquals(returnRequest.getReturnNumber(), result.get().getReturnNumber());
        assertEquals(returnRequest.getOrderNumber(), result.get().getOrderNumber());
        assertEquals(returnRequest.getSku(), result.get().getSku());
        assertEquals(returnRequest.getQuantity(), result.get().getQuantity());
        assertEquals(returnRequest.getReason(), result.get().getReason());
        assertEquals(returnRequest.getStatus(), result.get().getStatus());
        assertEquals(returnRequest.getRefundAmount(), result.get().getRefundAmount());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();

        final var result = returnRequestPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getReturnNumber(), result.get().getReturnNumber());
        assertEquals(entity.getOrderNumber(), result.get().getOrderNumber());
        assertEquals(entity.getSku(), result.get().getSku());
        assertEquals(entity.getQuantity(), result.get().getQuantity());
        assertEquals(entity.getReason(), result.get().getReason());
        assertEquals(entity.getStatus(), result.get().getStatus());
        assertEquals(entity.getRefundAmount(), result.get().getRefundAmount());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(returnRequestPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(returnRequestPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
