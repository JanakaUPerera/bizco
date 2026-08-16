package com.bizco.server.identity.repository;

import com.bizco.server.identity.entity.LoginHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    Page<LoginHistory> findAllByOrderByAttemptedAtDesc(Pageable pageable);

    Page<LoginHistory> findByUserIdOrderByAttemptedAtDesc(UUID userId, Pageable pageable);
}
