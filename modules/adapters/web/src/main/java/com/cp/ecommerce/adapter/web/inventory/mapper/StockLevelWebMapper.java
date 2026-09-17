package com.cp.ecommerce.adapter.web.inventory.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.inventory.resource.StockLevelResource;
import com.cp.ecommerce.domain.inventory.StockLevel;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link StockLevel} domain object to its web resource.
 */
@Component
public class StockLevelWebMapper implements WebResponseMapper<StockLevel, StockLevelResource> {

    @Override
    public Optional<StockLevelResource> mapToResource(final StockLevel stockLevel) {

        return Optional.ofNullable(stockLevel)
                .map(
                        domain -> StockLevelResource.builder()
                                .sku(domain.getSku())
                                .quantityOnHand(domain.getQuantityOnHand())
                                .quantityReserved(domain.getQuantityReserved())
                                .quantityAvailable(domain.getQuantityAvailable())
                                .build());
    }

}
