package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted read-model row derived from a consumed Kafka order-analytics event.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ORDER_ANALYTICS_PROJECTION")
public class OrderAnalyticsProjectionEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "orderAnalyticsProjectionSequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_ORDER_ANALYTICS_PROJECTION";

    @Id
    @Column(name = "ID", length = 13, nullable = false)
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    private Long id;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "CUSTOMER_ID", nullable = false)
    private Long customerId;

    @Column(name = "ORDER_PLACED_DATE", nullable = false)
    private Date orderPlacedDate;

    @Column(name = "CONSUMED_DATE", nullable = false)
    private Date consumedDate;

}
