package com.bizco.server.customer.application;

import java.util.List;
import java.util.UUID;

public interface CustomerAnonymizationEligibilityPort {
    List<String> blockersFor(UUID customerId);
}

