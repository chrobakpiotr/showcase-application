package com.cp.ecommerce.adapter.persistence.catalog.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.cp.ecommerce.domain.catalog.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Product} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "PRODUCT")
public class ProductEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "productSequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_PRODUCT";

    @Id
    @Column(name = "ID", length = 13, nullable = false)
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    private Long id;

    @Column(name = "SKU", length = 40, nullable = false, unique = true)
    private String sku;

    @Column(name = "NAME", length = 200, nullable = false)
    private String name;

    @Column(name = "DESCRIPTION", length = 2000)
    private String description;

    // Many products can share one category; unlike Order/Customer's ownership relationship (cascade ALL, one CustomerEntity
    // per order), a category is an independent aggregate that outlives any single product, so no cascade is configured here -
    // saving/deleting a product must never save/delete the category it points to.
    @ManyToOne(targetEntity = CategoryEntity.class, fetch = FetchType.EAGER)
    @JoinColumn(name = "CATEGORY_ID", nullable = false)
    private CategoryEntity category;

    @Column(name = "UNIT_PRICE", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "IMAGE_URL", length = 500)
    private String imageUrl;

    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    @Column(name = "CREATED_DATE", nullable = false)
    private Date created;

}
