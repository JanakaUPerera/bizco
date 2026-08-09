package com.bizco.server.identity.repository;

import com.bizco.server.identity.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, String> {
}
