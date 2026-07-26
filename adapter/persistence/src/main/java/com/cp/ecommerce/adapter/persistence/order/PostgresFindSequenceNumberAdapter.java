package com.cp.ecommerce.adapter.persistence.order;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.FindSequenceNumberOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.support.incrementer.PostgresSequenceMaxValueIncrementer;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Class which find next sequence number
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.jpa.database", havingValue = "postgresql")
public class PostgresFindSequenceNumberAdapter implements FindSequenceNumberOutPort {

    private final PostgresSequenceMaxValueIncrementer postgresSequenceMaxValueIncrementer;

    @Override
    public long find() {

        return postgresSequenceMaxValueIncrementer.nextIntValue();
    }

}
