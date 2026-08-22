package com.bizco.server.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A pick-list option for an {@link AttributeDataType#ENUM} attribute (e.g. Color: Red/Blue/
 *  Green). TEXT/NUMBER/BOOLEAN attributes never get rows here — enforced in
 *  {@code CatalogService}, not at the DB level (DatabaseDesign.md §55.2). */
@Entity
@Table(name = "attribute_values")
public class AttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attribute_value_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id")
    private Attribute attribute;

    @Column(nullable = false, length = 200)
    private String value;

    protected AttributeValue() {
    }

    public AttributeValue(final Attribute attribute, final String value) {
        this.attribute = attribute;
        this.value = value.trim();
    }

    public Long getId() { return id; }
    public Attribute getAttribute() { return attribute; }
    public String getValue() { return value; }
}
