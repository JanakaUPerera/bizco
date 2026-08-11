package com.bizco.server.customer.application;

import java.util.UUID;

public interface CustomerCreditQueryPort {
    CustomerReceivableSnapshot snapshotFor(UUID customerId);
}

