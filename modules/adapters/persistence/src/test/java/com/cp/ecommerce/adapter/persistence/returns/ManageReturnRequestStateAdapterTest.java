package com.cp.ecommerce.adapter.persistence.returns;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.exception.ReturnQuantityConflictException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotApprovableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRefundableException;
import com.cp.ecommerce.adapter.common.exception.ReturnRequestNotRejectableException;
import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ReturnRequestEntityBuilder;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;

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

}
