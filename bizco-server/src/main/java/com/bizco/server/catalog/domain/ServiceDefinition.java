package com.bizco.server.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_definitions")
public class ServiceDefinition {

    @Id
    @GeneratedValue
    @Column(name = "service_id")
    private UUID id;

    @Column(name = "service_code", nullable = false, length = 20)
    private String serviceCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "base_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes;

    @Column(name = "requires_estimate", nullable = false)
    private boolean requiresEstimate;

    @Column(name = "warranty_days", nullable = false)
    private int warrantyDays;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    protected ServiceDefinition() {
    }

    public ServiceDefinition(final String serviceCode, final String name, final String description,
                             final String category, final BigDecimal basePrice,
                             final int estimatedDurationMinutes, final boolean requiresEstimate,
                             final int warrantyDays) {
        update(serviceCode, name, description, category, basePrice, estimatedDurationMinutes,
                requiresEstimate, warrantyDays, true);
    }

    public void update(final String serviceCode, final String name, final String description,
                       final String category, final BigDecimal basePrice, final int estimatedDurationMinutes,
                       final boolean requiresEstimate, final int warrantyDays, final boolean active) {
        this.serviceCode = serviceCode.trim();
        this.name = name.trim();
        this.description = blankToNull(description);
        this.category = blankToNull(category);
        this.basePrice = basePrice;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.requiresEstimate = requiresEstimate;
        this.warrantyDays = warrantyDays;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void activate() { this.active = true; this.updatedAt = Instant.now(); }
    public void deactivate() { this.active = false; this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getServiceCode() { return serviceCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public BigDecimal getBasePrice() { return basePrice; }
    public int getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public boolean isRequiresEstimate() { return requiresEstimate; }
    public int getWarrantyDays() { return warrantyDays; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
