package com.cp.ecommerce.domain.customer.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.customer.port.incoming.ManageCustomerInPort;
import com.cp.ecommerce.domain.customer.port.outgoing.CheckCustomerOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for managing customer information and checking whether a customer already exists.
 */
@RequiredArgsConstructor
@UseCase
public class ManageCustomerUseCase implements ManageCustomerInPort {

    private final CheckCustomerOutPort checkCustomerOutPort;

    @Override
    public boolean checkCustomerExists(final String email) {

        return checkCustomerOutPort.check(email);
    }
}
