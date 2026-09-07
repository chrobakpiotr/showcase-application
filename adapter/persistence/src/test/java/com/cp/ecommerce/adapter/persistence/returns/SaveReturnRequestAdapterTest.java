package com.cp.ecommerce.adapter.persistence.returns;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntity;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ReturnRequestEntityBuilder;
import com.cp.ecommerce.domain.returns.ReturnRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link SaveReturnRequestAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveReturnRequestAdapterTest {

    @InjectMocks
    private transient SaveReturnRequestAdapter saveReturnRequestAdapter;

    @Mock
    private transient ReturnRequestEntityRepository returnRequestEntityRepository;

    @Mock
    private transient ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedReturnRequest() {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        final ReturnRequestEntity mappedEntity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(Optional.of(mappedEntity)).when(returnRequestPersistenceMapper).mapToEntity(eq(returnRequest));
        doReturn(mappedEntity).when(returnRequestEntityRepository).save(mappedEntity);
        doReturn(Optional.of(returnRequest)).when(returnRequestPersistenceMapper).mapToDomainObject(mappedEntity);

        final ReturnRequest result = saveReturnRequestAdapter.save(returnRequest);

        assertEquals(returnRequest, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        doReturn(Optional.empty()).when(returnRequestPersistenceMapper).mapToEntity(eq(returnRequest));

        assertThrows(IllegalStateException.class, () -> saveReturnRequestAdapter.save(returnRequest));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final ReturnRequest returnRequest = ReturnRequestBuilder.mockReturnRequest();
        final ReturnRequestEntity mappedEntity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(Optional.of(mappedEntity)).when(returnRequestPersistenceMapper).mapToEntity(eq(returnRequest));
        doReturn(mappedEntity).when(returnRequestEntityRepository).save(mappedEntity);
        doReturn(Optional.empty()).when(returnRequestPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveReturnRequestAdapter.save(returnRequest));
    }

}
