package com.bizco.server.system.repository;

import com.bizco.server.system.entity.BusinessProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {
}
