package com.cp.ecommerce.adapter.persistence.customer.entity;

import com.cp.ecommerce.domain.customer.Customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Class at the persistence layer representing {@link Customer} database repository.
 */
@Repository
public interface CustomerEntityRepository extends JpaRepository<CustomerEntity, Long> {

}
