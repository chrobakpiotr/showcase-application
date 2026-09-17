package com.cp.ecommerce.application;

import java.util.List;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_REMARKS;

/**
 * Test class that checks the correct operation of saving order to the database.
 */
@ActiveProfiles("local")
class ManageOrderIntegrationTest extends AbstractEcommerceApplicationTest {

    @Autowired
    transient ManageOrderUseCase manageOrderUseCase;

    @Autowired
    transient OrderEntityRepository orderEntityRepository;

    @Test
    void shouldSaveOrder() {

        final Order saved = manageOrderUseCase.saveOrder(OrderBuilder.mockOrder());
        final List<OrderEntity> orderEntities = orderEntityRepository.findAll();

        assertThat(saved).isNotNull();
        assertThat(saved.getRemarks()).isEqualTo(TEST_REMARKS);
        assertThat(saved.getOrderNumber()).isNotBlank();
        assertThat(orderEntities.size()).isEqualTo(1);
    }

}
