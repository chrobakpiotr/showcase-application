package com.cp.ecommerce.application;

import javax.sql.DataSource;

import com.cp.ecommerce.domain.order.port.outgoing.FindSequenceNumberOutPort;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class that checks whether app has started properly with the persistence layer.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractEcommerceApplicationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FindSequenceNumberOutPort findSequenceNumberOutPort;

    @Test
    @Order(1)
    void springContextShouldLoad(ApplicationContext context) {

        assertThat(context).isNotNull();
    }

    @Test
    @Order(2)
    void injectedComponentsShouldNotBeNull() {

        assertThat(dataSource).isNotNull();
        assertThat(jdbcTemplate).isNotNull();
        assertThat(entityManager).isNotNull();
    }

    @Test
    @Order(3)
    void shouldIncrementSequenceByOne() {

        final long sequenceNumber = findSequenceNumberOutPort.find();
        assertThat(findSequenceNumberOutPort.find()).isEqualTo(sequenceNumber + 1);
    }

}
