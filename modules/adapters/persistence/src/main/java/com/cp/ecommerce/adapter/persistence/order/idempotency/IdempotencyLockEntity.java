package com.cp.ecommerce.adapter.persistence.order.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Fixed, migration-owned rows used to arbitrate creation of previously unseen keys. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "IDEMPOTENCY_LOCK")
public class IdempotencyLockEntity {

    @Id
    private Integer id;

}
