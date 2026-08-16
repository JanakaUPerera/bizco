package com.bizco.server.identity.repository;

import com.bizco.server.identity.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    @Query("""
            select ur from UserRole ur
            join fetch ur.role
            where ur.user.id = :userId
              and ur.revokedAt is null
              and (ur.expiresAt is null or ur.expiresAt > :now)
            """)
    List<UserRole> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            select ur from UserRole ur
            join fetch ur.role
            where ur.user.id = :userId
            order by ur.grantedAt desc
            """)
    List<UserRole> findAllByUserId(@Param("userId") UUID userId);

    @Query("""
            select ur from UserRole ur
            join fetch ur.role
            join fetch ur.user
            where ur.active = true
              and ur.revokedAt is null
              and ur.expiresAt is not null
              and ur.expiresAt <= :now
            """)
    List<UserRole> findExpiredActive(@Param("now") Instant now);
}

