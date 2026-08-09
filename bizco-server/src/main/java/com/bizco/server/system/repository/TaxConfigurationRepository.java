package com.bizco.server.system.repository;

import com.bizco.server.system.entity.TaxConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxConfigurationRepository extends JpaRepository<TaxConfiguration, Short> {
}
