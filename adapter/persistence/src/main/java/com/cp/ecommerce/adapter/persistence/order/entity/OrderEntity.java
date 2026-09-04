package com.cp.ecommerce.adapter.persistence.order.entity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.persistence.customer.entity.CustomerEntity;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Order} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "ORDER_")
public class OrderEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "orderSequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_ORDER";

    @Id
    @Column(name = "ID", length = 13, nullable = false)
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    private Long id;

    @Column(name = "REMARK", length = 800, nullable = false)
    private String remarks;

    @Column(name = "ORDER_NUMBER", length = 40, nullable = false)
    private String orderNumber;

    @Column(name = "CREATION_DATE", length = 40, nullable = false)
    private Date created;

    @OneToOne(targetEntity = CustomerEntity.class, fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "CUSTOMER_ID")
    private CustomerEntity customer;

    // @ElementCollection, not a full child entity/repository, mirroring CartEntity.items (ADR 0027): a line item has
    // no identity or lifecycle independent of its owning order (see ADR 0029).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ORDER_LINE_ITEM", joinColumns = @JoinColumn(name = "ORDER_ID"))
    @Builder.Default
    private List<OrderLineItemEmbeddable> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAYMENT_METHOD", length = 20, nullable = false)
    private PaymentMethod paymentMethod;

}
