package com.bizco.server.system.repository;

import com.bizco.server.system.entity.SystemConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigRepository extends JpaRepository<SystemConfigEntry, String> {
}
