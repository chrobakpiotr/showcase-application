package com.cp.ecommerce.adapter.persistence.order;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOrderNumberOutPort;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Class responsible for generating order number.
 */
@PersistenceAdapter
@Transactional
@RequiredArgsConstructor
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
class GenerateOrderNumberAdapter implements GenerateOrderNumberOutPort {

    public static final String SEQUENCE_NUMBER_SEPARATOR = "-";

    @Override
    public String generate(final long sequenceNumber) {

        return UUID.randomUUID().toString().concat(SEQUENCE_NUMBER_SEPARATOR + sequenceNumber);
    }

}
