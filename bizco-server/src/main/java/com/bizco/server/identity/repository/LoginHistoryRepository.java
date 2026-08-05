package com.bizco.server.identity.repository;

import com.bizco.server.identity.entity.LoginHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {
}

