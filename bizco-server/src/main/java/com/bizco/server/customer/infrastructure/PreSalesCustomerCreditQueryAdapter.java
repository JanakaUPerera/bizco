package com.bizco.server.customer.infrastructure;

import com.bizco.server.customer.application.CustomerCreditQueryPort;
import com.bizco.server.customer.application.CustomerReceivableSnapshot;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PreSalesCustomerCreditQueryAdapter implements CustomerCreditQueryPort {

    @Override
    public CustomerReceivableSnapshot snapshotFor(final UUID customerId) {
        return CustomerReceivableSnapshot.zero();
    }
}

