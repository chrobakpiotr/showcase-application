package com.cp.ecommerce.application;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;
import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntityRepository;
import com.cp.ecommerce.domain.inventory.StockLevel;
import com.cp.ecommerce.domain.inventory.port.incoming.GetStockLevelInPort;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "outbox.publisher.enabled=false")
class StockRetryTransactionBoundaryPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @Autowired
    private ManageStockInPort manageStockInPort;

    @Autowired
    private GetStockLevelInPort getStockLevelInPort;

    @Autowired
    private StockLevelEntityRepository stockLevelEntityRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldRetryStockMutationInsideAmbientTransactionWithoutPoisoningIt() {

        final String sku = shortSku("R06");
        final String markerSku = shortSku("R06M");
        manageStockInPort.receiveStock(sku, 10);

        final TransactionTemplate outer = new TransactionTemplate(transactionManager);
        final TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final AtomicReference<StockLevel> result = new AtomicReference<>();

        outer.executeWithoutResult(status -> {
            final StockLevelEntity stale = stockLevelEntityRepository.findById(sku).orElseThrow();
            assertThat(stale.getQuantityOnHand()).isEqualTo(10);

            concurrent.executeWithoutResult(innerStatus -> {
                final StockLevelEntity winner = stockLevelEntityRepository.findById(sku).orElseThrow();
                winner.setQuantityOnHand(winner.getQuantityOnHand() + 1);
                stockLevelEntityRepository.saveAndFlush(winner);
            });

            result.set(manageStockInPort.receiveStock(sku, 5));

            stockLevelEntityRepository
                    .save(StockLevelEntity.builder().sku(markerSku).quantityOnHand(1).quantityReserved(0).version(0).build());
        });

        assertThat(result.get().getQuantityOnHand()).isEqualTo(16);
        assertThat(getStockLevelInPort.getStockLevel(sku).getQuantityOnHand()).isEqualTo(16);
        assertThat(stockLevelEntityRepository.findById(markerSku)).isPresent();
    }

    private static String shortSku(final String prefix) {

        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
