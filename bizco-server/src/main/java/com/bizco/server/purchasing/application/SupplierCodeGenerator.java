package com.bizco.server.purchasing.application;

import com.bizco.server.customer.infrastructure.DocumentSequenceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SupplierCodeGenerator {

    private final DocumentSequenceRepository sequenceRepository;

    public SupplierCodeGenerator(final DocumentSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextCode() {
        return "SUP-%05d".formatted(sequenceRepository.nextGlobalValue("SUPPLIER", "SUP", 5));
    }
}
