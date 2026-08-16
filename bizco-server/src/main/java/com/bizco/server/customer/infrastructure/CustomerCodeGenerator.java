package com.bizco.server.customer.infrastructure;

import com.bizco.server.system.infrastructure.DocumentSequenceRepository;
import org.springframework.stereotype.Component;

@Component
public class CustomerCodeGenerator {

    private final DocumentSequenceRepository sequences;

    public CustomerCodeGenerator(final DocumentSequenceRepository sequences) {
        this.sequences = sequences;
    }

    public String nextCode() {
        final long value = sequences.nextGlobalValue("CUSTOMER", "CUS", 6);
        return "CUS-" + String.format("%06d", value);
    }
}

