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

/** Assigns an {@link Attribute} to a {@link ProductCategory}, optionally marking it required.
 *  Drives the category-driven dynamic attribute form (MVP.md §4.7); the form itself is rendered
 *  client-side from a list of these. */
@Entity
@Table(name = "category_attributes")
public class CategoryAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_attribute_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id")
    private Attribute attribute;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    protected CategoryAttribute() {
    }

    public CategoryAttribute(final ProductCategory category, final Attribute attribute, final boolean required) {
        this.category = category;
        this.attribute = attribute;
        this.required = required;
    }

    public Long getId() { return id; }
    public ProductCategory getCategory() { return category; }
    public Attribute getAttribute() { return attribute; }
    public boolean isRequired() { return required; }
}
