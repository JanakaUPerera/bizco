package com.bizco.server.customer.infrastructure;

import com.bizco.server.customer.application.CustomerAnonymizationEligibilityPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NoDownstreamCustomerAnonymizationEligibilityAdapter implements CustomerAnonymizationEligibilityPort {

    @Override
    public List<String> blockersFor(final UUID customerId) {
        return List.of();
    }
}

