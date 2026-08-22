package com.bizco.server.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** One axis of a {@link ProductVariant}'s identity (e.g. Color=Red, Size=M), sourced from the
 *  attributes assigned to the product's category ({@link CategoryAttribute}). Exactly one of
 *  {@link #getAttributeValue()} (an {@link AttributeDataType#ENUM} pick) or {@link #getFreeValue()}
 *  (TEXT/NUMBER/BOOLEAN entered directly) is set, enforced by the table's {@code CHECK}
 *  (DatabaseDesign.md §56.2). */
@Entity
@Table(name = "variant_attribute_values")
public class VariantAttributeValue {

    @Id
    @GeneratedValue
    @Column(name = "variant_attribute_value_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_variant_id")
    private ProductVariant productVariant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id")
    private Attribute attribute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_value_id")
    private AttributeValue attributeValue;

    @Column(name = "free_value", length = 200)
    private String freeValue;

    protected VariantAttributeValue() {
    }

    private VariantAttributeValue(final ProductVariant productVariant, final Attribute attribute,
                                  final AttributeValue attributeValue, final String freeValue) {
        this.productVariant = productVariant;
        this.attribute = attribute;
        this.attributeValue = attributeValue;
        this.freeValue = freeValue;
    }

    public static VariantAttributeValue forEnumValue(final ProductVariant productVariant, final Attribute attribute,
                                                      final AttributeValue attributeValue) {
        return new VariantAttributeValue(productVariant, attribute, attributeValue, null);
    }

    public static VariantAttributeValue forFreeValue(final ProductVariant productVariant, final Attribute attribute,
                                                      final String freeValue) {
        return new VariantAttributeValue(productVariant, attribute, null, freeValue.trim());
    }

    public UUID getId() { return id; }
    public ProductVariant getProductVariant() { return productVariant; }
    public Attribute getAttribute() { return attribute; }
    public AttributeValue getAttributeValue() { return attributeValue; }
    public String getFreeValue() { return freeValue; }
}
