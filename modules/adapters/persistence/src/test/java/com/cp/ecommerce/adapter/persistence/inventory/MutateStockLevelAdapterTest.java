package com.cp.ecommerce.adapter.persistence.inventory;

import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.adapter.persistence.inventory.mapper.StockLevelPersistenceMapper;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.foundation.exception.StockLevelConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link MutateStockLevelAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class MutateStockLevelAdapterTest {

    private static final String SKU = "SKU-1001";

    @Mock
    private transient StockLevelEntityRepository repository;

    @Mock
    private transient StockLevelPersistenceMapper mapper;

    private transient MutateStockLevelAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new MutateStockLevelAdapter(repository, mapper);
    }

    @Test
    void shouldReadMutateAndSaveExistingStock() {

        final StockLevelEntity currentEntity = entity(10, 2, 3);
        final StockLevel current = stock(10, 2, 3);
        final StockLevel mutated = stock(15, 2, 3);
        final StockLevelEntity mutatedEntity = entity(15, 2, 3);
        final StockLevelEntity savedEntity = entity(15, 2, 4);
        final StockLevel saved = stock(15, 2, 4);

        given(repository.findById(SKU)).willReturn(Optional.of(currentEntity));
        given(mapper.mapToDomainObject(currentEntity)).willReturn(Optional.of(current));
        given(mapper.mapToEntity(mutated)).willReturn(Optional.of(mutatedEntity));
        given(repository.saveAndFlush(mutatedEntity)).willReturn(savedEntity);
        given(mapper.mapToDomainObject(savedEntity)).willReturn(Optional.of(saved));

        final StockLevel result = adapter.mutate(
                SKU,
                value -> StockLevel.builder()
                        .sku(SKU)
                        .quantityOnHand(value.getQuantityOnHand() + 5)
                        .quantityReserved(value.getQuantityReserved())
                        .version(value.getVersion())
                        .build());

        assertThat(result).isSameAs(saved);
    }

    @Test
    void shouldUseZeroStockWhenRowDoesNotExist() {

        final StockLevel mutated = stock(5, 0, 0);
        final StockLevelEntity mutatedEntity = entity(5, 0, 0);
        final StockLevel saved = stock(5, 0, 0);

        given(repository.findById(SKU)).willReturn(Optional.empty());
        given(mapper.mapToEntity(mutated)).willReturn(Optional.of(mutatedEntity));
        given(repository.saveAndFlush(mutatedEntity)).willReturn(mutatedEntity);
        given(mapper.mapToDomainObject(mutatedEntity)).willReturn(Optional.of(saved));

        final StockLevel result = adapter.mutate(
                SKU,
                value -> StockLevel.builder()
                        .sku(SKU)
                        .quantityOnHand(value.getQuantityOnHand() + 5)
                        .quantityReserved(value.getQuantityReserved())
                        .version(value.getVersion())
                        .build());

        assertThat(result.getQuantityOnHand()).isEqualTo(5);
    }

    @Test
    void shouldTranslateOptimisticLockFailure() {

        final StockLevelEntity currentEntity = entity(10, 2, 3);
        final StockLevel current = stock(10, 2, 3);
        final StockLevel mutated = stock(15, 2, 3);
        final StockLevelEntity mutatedEntity = entity(15, 2, 3);

        given(repository.findById(SKU)).willReturn(Optional.of(currentEntity));
        given(mapper.mapToDomainObject(currentEntity)).willReturn(Optional.of(current));
        given(mapper.mapToEntity(mutated)).willReturn(Optional.of(mutatedEntity));
        given(repository.saveAndFlush(mutatedEntity)).willThrow(new OptimisticLockingFailureException("stale"));

        assertThatThrownBy(
                () -> adapter.mutate(
                        SKU,
                        value -> StockLevel.builder()
                                .sku(SKU)
                                .quantityOnHand(value.getQuantityOnHand() + 5)
                                .quantityReserved(value.getQuantityReserved())
                                .version(value.getVersion())
                                .build()))
                .isInstanceOf(StockLevelConflictException.class);
    }

    @Test
    void shouldFailWhenCurrentEntityCannotBeMapped() {

        final StockLevelEntity currentEntity = entity(10, 2, 3);
        given(repository.findById(SKU)).willReturn(Optional.of(currentEntity));
        given(mapper.mapToDomainObject(currentEntity)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.mutate(SKU, value -> value)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SKU);
    }

    @Test
    void shouldFailWhenMutatedStockCannotBeMapped() {

        final StockLevelEntity currentEntity = entity(10, 2, 3);
        final StockLevel current = stock(10, 2, 3);

        given(repository.findById(SKU)).willReturn(Optional.of(currentEntity));
        given(mapper.mapToDomainObject(currentEntity)).willReturn(Optional.of(current));
        given(mapper.mapToEntity(current)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.mutate(SKU, value -> value)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SKU);
    }

    @Test
    void shouldFailWhenSavedEntityCannotBeMapped() {

        final StockLevelEntity currentEntity = entity(10, 2, 3);
        final StockLevel current = stock(10, 2, 3);
        final StockLevelEntity savedEntity = entity(10, 2, 4);

        given(repository.findById(SKU)).willReturn(Optional.of(currentEntity));
        given(mapper.mapToDomainObject(currentEntity)).willReturn(Optional.of(current));
        given(mapper.mapToEntity(current)).willReturn(Optional.of(currentEntity));
        given(repository.saveAndFlush(currentEntity)).willReturn(savedEntity);
        given(mapper.mapToDomainObject(savedEntity)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.mutate(SKU, value -> value)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SKU);
    }

    @Test
    void shouldPassMutatedValueToPersistenceMapper() {

        final StockLevelEntity currentEntity = entity(10, 2, 3);
        final StockLevel current = stock(10, 2, 3);
        final StockLevel mutated = stock(10, 5, 3);
        final StockLevelEntity mutatedEntity = entity(10, 5, 3);
        final StockLevel saved = stock(10, 5, 4);

        given(repository.findById(SKU)).willReturn(Optional.of(currentEntity));
        given(mapper.mapToDomainObject(currentEntity)).willReturn(Optional.of(current));
        given(mapper.mapToEntity(mutated)).willReturn(Optional.of(mutatedEntity));
        given(repository.saveAndFlush(mutatedEntity)).willReturn(mutatedEntity);
        given(mapper.mapToDomainObject(mutatedEntity)).willReturn(Optional.of(saved));

        adapter.mutate(
                SKU,
                value -> StockLevel.builder()
                        .sku(SKU)
                        .quantityOnHand(value.getQuantityOnHand())
                        .quantityReserved(value.getQuantityReserved() + 3)
                        .version(value.getVersion())
                        .build());

        verify(mapper).mapToEntity(mutated);
    }

    private static StockLevel stock(final int onHand, final int reserved, final long version) {

        return StockLevel.builder().sku(SKU).quantityOnHand(onHand).quantityReserved(reserved).version(version).build();
    }

    private static StockLevelEntity entity(final int onHand, final int reserved, final long version) {

        return StockLevelEntity.builder().sku(SKU).quantityOnHand(onHand).quantityReserved(reserved).version(version).build();
    }
}
