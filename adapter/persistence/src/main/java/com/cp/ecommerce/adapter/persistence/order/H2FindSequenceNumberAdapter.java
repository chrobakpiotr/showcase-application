package com.cp.ecommerce.adapter.persistence.order;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.FindSequenceNumberOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.support.incrementer.H2SequenceMaxValueIncrementer;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Class which find next sequence number
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.jpa.database", havingValue = "H2")
public class H2FindSequenceNumberAdapter implements FindSequenceNumberOutPort {

    private final H2SequenceMaxValueIncrementer h2SequenceMaxValueIncrementer;

    @Override
    public long find() {
        return h2SequenceMaxValueIncrementer.nextIntValue();
    }
}
