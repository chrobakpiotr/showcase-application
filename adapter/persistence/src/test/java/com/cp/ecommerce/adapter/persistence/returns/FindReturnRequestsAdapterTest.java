package com.cp.ecommerce.adapter.persistence.returns;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ReturnRequestEntityBuilder;
import com.cp.ecommerce.domain.returns.ReturnStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link FindReturnRequestsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindReturnRequestsAdapterTest {

    @InjectMocks
    private transient FindReturnRequestsAdapter findReturnRequestsAdapter;

    @Mock
    private transient ReturnRequestEntityRepository returnRequestEntityRepository;

    @Mock
    private transient ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    @Test
    void shouldFindAllReturnRequests() {

        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(List.of(entity)).when(returnRequestEntityRepository).findAllByOrderByRequestedDateDesc();
        doReturn(Optional.of(ReturnRequestBuilder.mockReturnRequest())).when(returnRequestPersistenceMapper)
                .mapToDomainObject(eq(entity));

        assertThat(findReturnRequestsAdapter.findAll()).hasSize(1);
    }

    @Test
    void shouldFindPendingReturnRequests() {

        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(List.of(entity)).when(returnRequestEntityRepository)
                .findByStatusOrderByRequestedDateAsc(ReturnStatus.REQUESTED);
        doReturn(Optional.of(ReturnRequestBuilder.mockReturnRequest())).when(returnRequestPersistenceMapper)
                .mapToDomainObject(eq(entity));

        assertThat(findReturnRequestsAdapter.findPending()).hasSize(1);
    }

    @Test
    void shouldFindReturnRequestsByOrderNumber() {

        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(List.of(entity)).when(returnRequestEntityRepository)
                .findByOrderNumberOrderByRequestedDateDesc(ReturnRequestBuilder.TEST_ORDER_NUMBER);
        doReturn(Optional.of(ReturnRequestBuilder.mockReturnRequest())).when(returnRequestPersistenceMapper)
                .mapToDomainObject(eq(entity));

        assertThat(findReturnRequestsAdapter.findByOrderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)).hasSize(1);
    }

    @Test
    void shouldThrowWhenFindAllCannotMapReturnRequest() {

        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(List.of(entity)).when(returnRequestEntityRepository).findAllByOrderByRequestedDateDesc();
        doReturn(Optional.empty()).when(returnRequestPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findReturnRequestsAdapter.findAll()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReturnRequestBuilder.TEST_RETURN_NUMBER);
    }

    @Test
    void shouldThrowWhenFindPendingCannotMapReturnRequest() {

        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(List.of(entity)).when(returnRequestEntityRepository)
                .findByStatusOrderByRequestedDateAsc(ReturnStatus.REQUESTED);
        doReturn(Optional.empty()).when(returnRequestPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findReturnRequestsAdapter.findPending()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReturnRequestBuilder.TEST_RETURN_NUMBER);
    }

}
