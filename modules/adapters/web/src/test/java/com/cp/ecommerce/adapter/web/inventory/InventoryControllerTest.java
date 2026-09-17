package com.cp.ecommerce.adapter.web.inventory;

import com.cp.ecommerce.adapter.common.utils.StockLevelBuilder;
import com.cp.ecommerce.adapter.web.inventory.mapper.StockLevelWebMapper;
import com.cp.ecommerce.adapter.web.inventory.resource.StockLevelResource;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.incoming.GetStockLevelInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.cp.ecommerce.adapter.common.utils.StockLevelBuilder.TEST_STOCK_SKU;

/**
 * Test class checking inventory controller's behavior and API responses.
 */
@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    private static final String INVENTORY_ENDPOINT = "/api/inventory/" + TEST_STOCK_SKU;

    private static final String RECEIVE_PATH = INVENTORY_ENDPOINT + "/receive";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private transient GetStockLevelInPort getStockLevelInPort;

    @MockitoBean
    private transient ManageStockInPort manageStockInPort;

    @MockitoBean
    private transient StockLevelWebMapper stockLevelWebMapper;

    @Test
    void shouldGetStockLevel() throws Exception {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        given(getStockLevelInPort.getStockLevel(TEST_STOCK_SKU)).willReturn(stockLevel);
        given(stockLevelWebMapper.mapToResource(stockLevel)).willReturn(java.util.Optional.of(mockStockLevelResource()));

        mockMvc.perform(get(INVENTORY_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(TEST_STOCK_SKU))
                .andExpect(jsonPath("$.quantityOnHand").value(10))
                .andExpect(jsonPath("$.quantityReserved").value(2))
                .andExpect(jsonPath("$.quantityAvailable").value(8));
    }

    @Test
    void shouldReceiveStock() throws Exception {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        given(manageStockInPort.receiveStock(TEST_STOCK_SKU, 5)).willReturn(stockLevel);
        given(stockLevelWebMapper.mapToResource(stockLevel)).willReturn(java.util.Optional.of(mockStockLevelResource()));

        mockMvc.perform(post(RECEIVE_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value(TEST_STOCK_SKU));
    }

    @Test
    void shouldReserveStock() throws Exception {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        given(manageStockInPort.reserveStock(TEST_STOCK_SKU, 3)).willReturn(stockLevel);
        given(stockLevelWebMapper.mapToResource(stockLevel)).willReturn(java.util.Optional.of(mockStockLevelResource()));

        mockMvc.perform(
                post(INVENTORY_ENDPOINT + "/reserve").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReleaseStock() throws Exception {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        given(manageStockInPort.releaseStock(TEST_STOCK_SKU, 1)).willReturn(stockLevel);
        given(stockLevelWebMapper.mapToResource(stockLevel)).willReturn(java.util.Optional.of(mockStockLevelResource()));

        mockMvc.perform(
                post(INVENTORY_ENDPOINT + "/release").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldFulfillStock() throws Exception {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        given(manageStockInPort.fulfillStock(TEST_STOCK_SKU, 2)).willReturn(stockLevel);
        given(stockLevelWebMapper.mapToResource(stockLevel)).willReturn(java.util.Optional.of(mockStockLevelResource()));

        mockMvc.perform(
                post(INVENTORY_ENDPOINT + "/fulfill").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectReceiveStockWithMissingQuantity() throws Exception {

        mockMvc.perform(post(RECEIVE_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(manageStockInPort, never()).receiveStock(anyString(), anyInt());
    }

    @Test
    void shouldRejectReceiveStockWithZeroQuantity() throws Exception {

        mockMvc.perform(post(RECEIVE_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());
        verify(manageStockInPort, never()).receiveStock(anyString(), anyInt());
    }

    @Test
    void shouldRejectReceiveStockWithNegativeQuantity() throws Exception {

        mockMvc.perform(post(RECEIVE_PATH).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":-1}"))
                .andExpect(status().isBadRequest());
        verify(manageStockInPort, never()).receiveStock(anyString(), anyInt());
    }

    @Test
    void shouldThrowTechnicalProblemWhenMapToResourceReturnsEmpty() throws Exception {

        final StockLevel stockLevel = StockLevelBuilder.mockStockLevel();
        given(getStockLevelInPort.getStockLevel(TEST_STOCK_SKU)).willReturn(stockLevel);
        given(stockLevelWebMapper.mapToResource(stockLevel)).willReturn(java.util.Optional.empty());

        mockMvc.perform(get(INVENTORY_ENDPOINT)).andExpect(status().isInternalServerError());
    }

    private static StockLevelResource mockStockLevelResource() {

        return StockLevelResource.builder()
                .sku(TEST_STOCK_SKU)
                .quantityOnHand(10)
                .quantityReserved(2)
                .quantityAvailable(8)
                .build();
    }

}
