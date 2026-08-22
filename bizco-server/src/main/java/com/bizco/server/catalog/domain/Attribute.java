package com.bizco.server.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An admin-defined product specification (e.g. "Color", "RAM") that can be assigned to
 *  categories ({@link CategoryAttribute}) and, from Phase 6 Week 17 onward, given values per
 *  product variant. Unlike {@link Brand}/{@link com.bizco.server.catalog.domain.ProductCategory},
 *  attributes have no {@code is_active}/version — they're a fixed vocabulary managed by admins,
 *  not a record edited over time (DatabaseDesign.md §55.2). */
@Entity
@Table(name = "attributes")
public class Attribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attribute_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private AttributeDataType dataType;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Attribute() {
    }

    public Attribute(final String name, final AttributeDataType dataType) {
        this.name = name.trim();
        this.dataType = dataType;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public AttributeDataType getDataType() { return dataType; }
    public Instant getCreatedAt() { return createdAt; }
}
