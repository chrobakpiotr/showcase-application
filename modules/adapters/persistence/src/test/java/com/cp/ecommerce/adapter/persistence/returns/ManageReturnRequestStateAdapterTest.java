package com.cp.ecommerce.adapter.persistence.returns;

import java.math.BigDecimal;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ReturnRequestEntityBuilder;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.foundation.exception.ReturnQuantityConflictException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotRefundableException;
import com.cp.ecommerce.foundation.exception.ReturnRequestNotRejectableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ManageReturnRequestStateAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class ManageReturnRequestStateAdapterTest {

    private static final String ONE_CENT = "0.01";

    @InjectMocks
    private transient ManageReturnRequestStateAdapter adapter;

    @Mock
    private transient ReturnRequestEntityRepository returnRequestEntityRepository;

    @Mock
    private transient ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    private ReturnRequest request;
    private ReturnRequestEntity entity;

    @BeforeEach
    void setUp() {

        request = ReturnRequestBuilder.mockReturnRequest();
        entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
    }

    @Test
    void shouldCreateWhenEntitlementRemains() {

        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(request.getOrderNumber(), request.getSku(), ReturnStatus.REJECTED))
                .willReturn(0L);
        given(returnRequestPersistenceMapper.mapToEntity(request)).willReturn(Optional.of(entity));
        given(returnRequestEntityRepository.saveAndFlush(entity)).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(request));

        assertThat(adapter.create(request, request.getQuantity())).isSameAs(request);
    }

    @Test
    void shouldPreserveRequestedRefundAmountForLegacyPortContract() {

        final BigDecimal requestedRefund = new BigDecimal("30.00");
        final ReturnRequest directRequest = ReturnRequest.builder()
                .returnNumber(request.getReturnNumber())
                .orderNumber(request.getOrderNumber())
                .sku(request.getSku())
                .quantity(1)
                .reason(request.getReason())
                .status(ReturnStatus.REQUESTED)
                .requestedDate(request.getRequestedDate())
                .refundAmount(requestedRefund)
                .build();
        final ReturnRequestEntity directEntity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        directEntity.setRefundAmount(requestedRefund);

        given(orderEntityRepository.findByOrderNumberForUpdate(directRequest.getOrderNumber()))
                .willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(directRequest.getOrderNumber(), directRequest.getSku(), ReturnStatus.REJECTED))
                .willReturn(0L);
        given(returnRequestPersistenceMapper.mapToEntity(directRequest)).willReturn(Optional.of(directEntity));
        given(returnRequestEntityRepository.saveAndFlush(directEntity)).willReturn(directEntity);
        given(returnRequestPersistenceMapper.mapToDomainObject(directEntity)).willReturn(Optional.of(directRequest));

        adapter.create(directRequest, 2);

        assertThat(directEntity.getRefundAmount()).isEqualByComparingTo(requestedRefund);
    }

    @Test
    void shouldAllocateFullLineEntitlementAcrossSequentialPartialReturns() {

        final BigDecimal fullLineEntitlement = new BigDecimal("60.00");
        final ReturnRequest first = ReturnRequest.builder()
                .returnNumber(request.getReturnNumber())
                .orderNumber(request.getOrderNumber())
                .sku(request.getSku())
                .quantity(1)
                .reason(request.getReason())
                .status(ReturnStatus.REQUESTED)
                .requestedDate(request.getRequestedDate())
                .refundAmount(fullLineEntitlement)
                .build();
        final ReturnRequestEntity firstEntity = ReturnRequestEntityBuilder.mockReturnRequestEntity();

        given(orderEntityRepository.findByOrderNumberForUpdate(first.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(returnRequestEntityRepository.sumActiveQuantity(first.getOrderNumber(), first.getSku(), ReturnStatus.REJECTED))
                .willReturn(0L);
        given(returnRequestPersistenceMapper.mapToEntity(first)).willReturn(Optional.of(firstEntity));
        given(returnRequestEntityRepository.saveAndFlush(firstEntity)).willReturn(firstEntity);
        given(returnRequestPersistenceMapper.mapToDomainObject(firstEntity)).willReturn(Optional.of(first));

        adapter.createFromLineEntitlement(first, 2);

        assertThat(firstEntity.getRefundAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void shouldRestoreReleasedMinorUnitFromPersistedActiveRefundSnapshot() {

        final BigDecimal fullLineEntitlement = new BigDecimal(ONE_CENT);
        final ReturnRequest allocatedRequest = ReturnRequest.builder()
                .returnNumber(request.getReturnNumber())
                .orderNumber(request.getOrderNumber())
                .sku(request.getSku())
                .quantity(1)
                .reason(request.getReason())
                .status(ReturnStatus.REQUESTED)
                .requestedDate(request.getRequestedDate())
                .refundAmount(fullLineEntitlement)
                .build();
        final ReturnRequestEntity allocatedEntity = ReturnRequestEntityBuilder.mockReturnRequestEntity();

        given(orderEntityRepository.findByOrderNumberForUpdate(allocatedRequest.getOrderNumber()))
                .willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(allocatedRequest.getOrderNumber(), allocatedRequest.getSku(), ReturnStatus.REJECTED))
                .willReturn(1L);
        given(
                returnRequestEntityRepository.sumActiveRefundAmount(
                        allocatedRequest.getOrderNumber(),
                        allocatedRequest.getSku(),
                        ReturnStatus.REJECTED))
                .willReturn(BigDecimal.ZERO);
        given(returnRequestPersistenceMapper.mapToEntity(allocatedRequest)).willReturn(Optional.of(allocatedEntity));
        given(returnRequestEntityRepository.saveAndFlush(allocatedEntity)).willReturn(allocatedEntity);
        given(returnRequestPersistenceMapper.mapToDomainObject(allocatedEntity)).willReturn(Optional.of(allocatedRequest));

        adapter.createFromLineEntitlement(allocatedRequest, 3);

        assertThat(allocatedEntity.getRefundAmount()).isEqualByComparingTo(ONE_CENT);
    }

    @Test
    void shouldFailClosedWhenPersistedActiveRefundExceedsLineEntitlement() {

        final BigDecimal fullLineEntitlement = new BigDecimal(ONE_CENT);
        final ReturnRequest corruptRequest = ReturnRequest.builder()
                .returnNumber(request.getReturnNumber())
                .orderNumber(request.getOrderNumber())
                .sku(request.getSku())
                .quantity(1)
                .reason(request.getReason())
                .status(ReturnStatus.REQUESTED)
                .requestedDate(request.getRequestedDate())
                .refundAmount(fullLineEntitlement)
                .build();

        given(orderEntityRepository.findByOrderNumberForUpdate(corruptRequest.getOrderNumber()))
                .willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(corruptRequest.getOrderNumber(), corruptRequest.getSku(), ReturnStatus.REJECTED))
                .willReturn(1L);
        given(
                returnRequestEntityRepository
                        .sumActiveRefundAmount(corruptRequest.getOrderNumber(), corruptRequest.getSku(), ReturnStatus.REJECTED))
                .willReturn(new BigDecimal("0.02"));

        given(returnRequestPersistenceMapper.mapToEntity(corruptRequest)).willReturn(Optional.of(entity));

        assertThatThrownBy(() -> adapter.createFromLineEntitlement(corruptRequest, 3)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceed");
    }

    @Test
    void shouldRejectCreateWhenEntitlementIsExhausted() {

        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(request.getOrderNumber(), request.getSku(), ReturnStatus.REJECTED))
                .willReturn((long) request.getQuantity());

        assertThatThrownBy(() -> adapter.create(request, request.getQuantity()))
                .isInstanceOf(ReturnQuantityConflictException.class)
                .hasMessageContaining("remaining returnable quantity of 0");
    }

    @Test
    void shouldClampNegativeRemainingEntitlementToZero() {

        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(request.getOrderNumber(), request.getSku(), ReturnStatus.REJECTED))
                .willReturn((long) request.getQuantity() + 10L);

        assertThatThrownBy(() -> adapter.create(request, request.getQuantity()))
                .isInstanceOf(ReturnQuantityConflictException.class)
                .hasMessageContaining("remaining returnable quantity of 0");
    }

    @Test
    void shouldFailCreateWhenOrderDisappeared() {

        assertThatThrownBy(() -> adapter.create(request, request.getQuantity())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(request.getOrderNumber());
    }

    @Test
    void shouldFailCreateWhenDomainCannotBeMappedToEntity() {

        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(request.getOrderNumber(), request.getSku(), ReturnStatus.REJECTED))
                .willReturn(0L);
        given(returnRequestPersistenceMapper.mapToEntity(request)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.create(request, request.getQuantity())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(request.getReturnNumber());
    }

    @Test
    void shouldFailCreateWhenSavedEntityCannotBeMappedBack() {

        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(
                returnRequestEntityRepository
                        .sumActiveQuantity(request.getOrderNumber(), request.getSku(), ReturnStatus.REJECTED))
                .willReturn(0L);
        given(returnRequestPersistenceMapper.mapToEntity(request)).willReturn(Optional.of(entity));
        given(returnRequestEntityRepository.saveAndFlush(entity)).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.create(request, request.getQuantity())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(request.getReturnNumber());
    }

    @Test
    void shouldReturnNullWhenApproveTargetDoesNotExist() {

        assertThat(adapter.approve(request.getReturnNumber())).isNull();
    }

    @Test
    void shouldRejectApprovalAfterRejection() {

        entity.setStatus(ReturnStatus.REJECTED);
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);

        assertThatThrownBy(() -> adapter.approve(request.getReturnNumber()))
                .isInstanceOf(ReturnRequestNotApprovableException.class);
    }

    @Test
    void shouldReturnExistingApprovedRequest() {

        entity.setStatus(ReturnStatus.APPROVED);
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(request));

        assertThat(adapter.approve(request.getReturnNumber())).isSameAs(request);
        verify(returnRequestEntityRepository, never()).saveAndFlush(entity);
    }

    @Test
    void shouldApproveRequestedReturn() {

        entity.setStatus(ReturnStatus.REQUESTED);
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);
        given(returnRequestEntityRepository.saveAndFlush(entity)).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(request));

        assertThat(adapter.approve(request.getReturnNumber())).isSameAs(request);
        assertThat(entity.getStatus()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(entity.getDecidedDate()).isNotNull();
    }

    @Test
    void shouldReturnNullWhenRejectTargetDoesNotExist() {

        assertThat(adapter.reject(request.getReturnNumber())).isNull();
    }

    @Test
    void shouldFailRejectWhenOrderDisappeared() {

        given(returnRequestEntityRepository.findOrderNumberByReturnNumber(request.getReturnNumber()))
                .willReturn(request.getOrderNumber());

        assertThatThrownBy(() -> adapter.reject(request.getReturnNumber())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(request.getOrderNumber());
    }

    @Test
    void shouldReturnNullWhenRejectTargetDisappearsAfterOrderLock() {

        given(returnRequestEntityRepository.findOrderNumberByReturnNumber(request.getReturnNumber()))
                .willReturn(request.getOrderNumber());
        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));

        assertThat(adapter.reject(request.getReturnNumber())).isNull();
    }

    @Test
    void shouldReturnExistingRejectedRequestWithoutSecondRelease() {

        entity.setStatus(ReturnStatus.REJECTED);
        given(returnRequestEntityRepository.findOrderNumberByReturnNumber(request.getReturnNumber()))
                .willReturn(request.getOrderNumber());
        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(request));

        assertThat(adapter.reject(request.getReturnNumber())).isSameAs(request);
        verify(returnRequestEntityRepository, never()).saveAndFlush(entity);
    }

    @Test
    void shouldRejectOnlyRequestedReturn() {

        entity.setStatus(ReturnStatus.APPROVED);
        given(returnRequestEntityRepository.findOrderNumberByReturnNumber(request.getReturnNumber()))
                .willReturn(request.getOrderNumber());
        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);

        assertThatThrownBy(() -> adapter.reject(request.getReturnNumber()))
                .isInstanceOf(ReturnRequestNotRejectableException.class);
    }

    @Test
    void shouldRejectRequestedReturn() {

        entity.setStatus(ReturnStatus.REQUESTED);
        given(returnRequestEntityRepository.findOrderNumberByReturnNumber(request.getReturnNumber()))
                .willReturn(request.getOrderNumber());
        given(orderEntityRepository.findByOrderNumberForUpdate(request.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);
        given(returnRequestEntityRepository.saveAndFlush(entity)).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(request));

        assertThat(adapter.reject(request.getReturnNumber())).isSameAs(request);
        assertThat(entity.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(entity.getDecidedDate()).isNotNull();
    }

    @Test
    void shouldReturnNullWhenRefundTargetDoesNotExist() {

        assertThat(adapter.markRefunded(request.getReturnNumber())).isNull();
    }

    @Test
    void shouldReturnExistingRefundedRequest() {

        entity.setStatus(ReturnStatus.REFUNDED);
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(request));

        assertThat(adapter.markRefunded(request.getReturnNumber())).isSameAs(request);
        verify(returnRequestEntityRepository, never()).saveAndFlush(entity);
    }

    @Test
    void shouldRejectRefundBeforeApproval() {

        entity.setStatus(ReturnStatus.REQUESTED);
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);

        assertThatThrownBy(() -> adapter.markRefunded(request.getReturnNumber()))
                .isInstanceOf(ReturnRequestNotRefundableException.class);
    }

    @Test
    void shouldMarkApprovedRequestRefunded() {

        entity.setStatus(ReturnStatus.APPROVED);
        given(returnRequestEntityRepository.findByReturnNumberForUpdate(request.getReturnNumber())).willReturn(entity);
        given(returnRequestEntityRepository.saveAndFlush(entity)).willReturn(entity);
        given(returnRequestPersistenceMapper.mapToDomainObject(entity)).willReturn(Optional.of(request));

        assertThat(adapter.markRefunded(request.getReturnNumber())).isSameAs(request);
        assertThat(entity.getStatus()).isEqualTo(ReturnStatus.REFUNDED);
    }

    @Test
    void shouldAllocateMinorUnitRemainderToEarliestReturnedUnit() {

        final BigDecimal fullLineEntitlement = new BigDecimal(ONE_CENT);
        final ReturnRequest first = ReturnRequest.builder()
                .returnNumber(request.getReturnNumber())
                .orderNumber(request.getOrderNumber())
                .sku(request.getSku())
                .quantity(1)
                .reason(request.getReason())
                .status(ReturnStatus.REQUESTED)
                .requestedDate(request.getRequestedDate())
                .refundAmount(fullLineEntitlement)
                .build();
        final ReturnRequestEntity firstEntity = ReturnRequestEntityBuilder.mockReturnRequestEntity();

        given(orderEntityRepository.findByOrderNumberForUpdate(first.getOrderNumber())).willReturn(mock(OrderEntity.class));
        given(returnRequestEntityRepository.sumActiveQuantity(first.getOrderNumber(), first.getSku(), ReturnStatus.REJECTED))
                .willReturn(0L);
        given(returnRequestPersistenceMapper.mapToEntity(first)).willReturn(Optional.of(firstEntity));
        given(returnRequestEntityRepository.saveAndFlush(firstEntity)).willReturn(firstEntity);
        given(returnRequestPersistenceMapper.mapToDomainObject(firstEntity)).willReturn(Optional.of(first));

        adapter.createFromLineEntitlement(first, 2);

        assertThat(firstEntity.getRefundAmount()).isEqualByComparingTo(ONE_CENT);
    }

}
