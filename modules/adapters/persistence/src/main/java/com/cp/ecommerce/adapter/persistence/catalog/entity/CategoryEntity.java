package com.cp.ecommerce.adapter.persistence.catalog.entity;

import com.cp.ecommerce.domain.catalog.Category;

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
 * Representation of {@link Category} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "CATEGORY")
public class CategoryEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "categorySequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_CATEGORY";

    @Id
    @Column(name = "ID", length = 13, nullable = false)
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    private Long id;

    @Column(name = "NAME", length = 120, nullable = false)
    private String name;

    @Column(name = "SLUG", length = 120, nullable = false, unique = true)
    private String slug;

}
