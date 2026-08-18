package com.bizco.server.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Operational technician-eligibility flag for a {@link User} (V003, DatabaseDesign.md &sect;9).
 * Keyed 1:1 off {@code users.user_id} rather than its own generated id - a staff profile only
 * ever describes an existing user. Deliberately independent of RBAC: technician assignment is an
 * operational capability, not a permission-role identity (DomainModel.md &sect;13.7), so a user
 * can be schedulable without holding any special security role.
 */
@Entity
@Table(name = "staff_profiles")
public class StaffProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Column(name = "is_technician", nullable = false)
    private boolean technician;
    @Column(name = "display_name", length = 150)
    private String displayName;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StaffProfile() {
    }

    public StaffProfile(final UUID userId, final boolean technician, final String displayName) {
        this.userId = userId;
        this.technician = technician;
        this.displayName = displayName;
    }

    public void update(final boolean technician, final String displayName) {
        this.technician = technician;
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isTechnician() {
        return technician;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
