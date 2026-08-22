-- Phase 6 / Week 17, Step 1 of the DatabaseDesign.md §56.3 retrofit sequence: schema only, no
-- data yet. Never combine this with the backfill (V026) or the product_variant_id columns
-- (V027) in one migration -- each step must be separately committed and independently
-- verifiable per §56.3's explicit warning.
--
-- Design (DatabaseDesign.md §56.1): every product gets exactly one product_variants row, even a
-- product with no real variation (its "default variant") -- there is no special-casing between
-- simple and varianted products anywhere downstream. `products` stays the style/parent for now
-- (its sku/barcode/pricing columns are not dropped until Week 18 Step 6); `product_variants`
-- becomes the SKU/barcode/price/stock/reorder unit going forward.

CREATE TABLE product_variants (
    product_variant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL,
    sku VARCHAR(20) NOT NULL,
    barcode VARCHAR(50),
    variant_label VARCHAR(200),
    cost_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    selling_price NUMERIC(15,2) NOT NULL,
    wholesale_price NUMERIC(15,2),
    reorder_point NUMERIC(15,3) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    image_path VARCHAR(500),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_product_variants_sku UNIQUE (sku),
    CONSTRAINT uq_product_variants_barcode UNIQUE (barcode),
    CONSTRAINT ck_product_variants_cost CHECK (cost_price >= 0),
    CONSTRAINT ck_product_variants_selling CHECK (selling_price >= 0),
    CONSTRAINT ck_product_variants_wholesale CHECK (wholesale_price IS NULL OR wholesale_price >= 0),
    CONSTRAINT ck_product_variants_reorder CHECK (reorder_point >= 0),

    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_product_variants_product ON product_variants(product_id);

-- Exactly one default variant per product.
CREATE UNIQUE INDEX uq_product_variants_default ON product_variants(product_id) WHERE is_default = TRUE;

CREATE TABLE variant_attribute_values (
    variant_attribute_value_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_variant_id UUID NOT NULL,
    attribute_id BIGINT NOT NULL,
    attribute_value_id BIGINT,
    free_value VARCHAR(200),

    CONSTRAINT uq_variant_attribute UNIQUE (product_variant_id, attribute_id),
    CONSTRAINT ck_variant_attribute_value CHECK (
        (attribute_value_id IS NOT NULL AND free_value IS NULL)
        OR
        (attribute_value_id IS NULL AND free_value IS NOT NULL)
    ),

    CONSTRAINT fk_variant_attribute_values_variant FOREIGN KEY (product_variant_id)
        REFERENCES product_variants(product_variant_id),
    CONSTRAINT fk_variant_attribute_values_attribute FOREIGN KEY (attribute_id)
        REFERENCES attributes(attribute_id),
    CONSTRAINT fk_variant_attribute_values_value FOREIGN KEY (attribute_value_id)
        REFERENCES attribute_values(attribute_value_id)
);

CREATE INDEX idx_variant_attribute_values_variant ON variant_attribute_values(product_variant_id);
CREATE INDEX idx_variant_attribute_values_attribute ON variant_attribute_values(attribute_id);
