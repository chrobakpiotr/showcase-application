package com.cp.ecommerce.adapter.persistence.customer.entity;

import com.cp.ecommerce.domain.customer.Customer;

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
 * Representation of {@link Customer} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "CUSTOMER")
public class CustomerEntity {

    private static final String SEQUENCE_GENERATOR_NAME = "customerSequenceGenerator";
    private static final String SEQUENCE_NAME = "SEQ_CUSTOMER";
    @Column(name = "FULL_NAME", length = 80)
    String fullName;
    @Column(name = "EMAIL", nullable = false)
    String email;
    @Column(name = "PHONE", length = 25, nullable = false)
    String phone;
    @Column(name = "STREET", length = 35)
    String street;
    @Column(name = "POSTAL_CODE", length = 35)
    String postalCode;
    @Column(name = "CITY", length = 300)
    String city;
    @Column(name = "COUNTRY_CODE", length = 2)
    String countryCode;
    @Id
    @SequenceGenerator(name = SEQUENCE_GENERATOR_NAME, sequenceName = SEQUENCE_NAME, allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = SEQUENCE_GENERATOR_NAME)
    @Column(name = "ID", length = 13, nullable = false)
    private Long id;

}
