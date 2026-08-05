package com.bizco.server.identity.repository;

import com.bizco.server.identity.entity.UserSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<UserSession> findByTokenHash(String tokenHash);

    List<UserSession> findByUserIdAndRevokedAtIsNull(UUID userId);
}
