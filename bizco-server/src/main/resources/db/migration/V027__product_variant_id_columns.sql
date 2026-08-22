-- Phase 6 / Week 17, Step 3 of the DatabaseDesign.md §56.3 retrofit sequence: add nullable
-- product_variant_id to every table that currently references products(product_id) directly,
-- backfilled from the Step 2 (V026) default-variant mapping. Both product_id and
-- product_variant_id stay populated and consistent from here through Week 18's application
-- cutover -- this migration does not repoint any repository/service/query.
--
-- credit_note_lines is deliberately NOT included: it has no product_id column today -- it
-- resolves the product by joining original_invoice_line_id -> invoice_lines.product_id
-- (CreditNoteService#applyReturn reads original.getProductId()). Adding a redundant
-- product_variant_id here would be new denormalization the existing design doesn't have for
-- product_id either; Week 18's CreditNoteService resolves productVariantId the same way, via
-- the same join.
--
-- product_cost_history is included even though DatabaseDesign.md §56.3's list doesn't name it
-- explicitly: it's a 1:1 append-only audit trail derived from goods_receipt_items (which *is*
-- on the list), and leaving it product-level would blend cost history across variants of the
-- same product once variants diverge in price.

-- invoice_lines.product_id is nullable (SERVICE/CUSTOM lines) -- the UPDATE below naturally
-- leaves product_variant_id null wherever product_id is null, which is correct.
ALTER TABLE invoice_lines ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE invoice_lines t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_invoice_lines_variant ON invoice_lines(product_variant_id);

ALTER TABLE held_sale_items ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE held_sale_items t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_held_sale_items_variant ON held_sale_items(product_variant_id);

ALTER TABLE job_parts ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE job_parts t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_job_parts_variant ON job_parts(product_variant_id);

ALTER TABLE stock_movements ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE stock_movements t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_stock_movements_variant ON stock_movements(product_variant_id);

ALTER TABLE stock_adjustments ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE stock_adjustments t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_stock_adjustments_variant ON stock_adjustments(product_variant_id);

ALTER TABLE supplier_products ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE supplier_products t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_supplier_products_variant ON supplier_products(product_variant_id);

ALTER TABLE purchase_order_items ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE purchase_order_items t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_purchase_order_items_variant ON purchase_order_items(product_variant_id);

ALTER TABLE goods_receipt_items ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE goods_receipt_items t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_goods_receipt_items_variant ON goods_receipt_items(product_variant_id);

ALTER TABLE supplier_return_items ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE supplier_return_items t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_supplier_return_items_variant ON supplier_return_items(product_variant_id);

ALTER TABLE product_cost_history ADD COLUMN product_variant_id UUID REFERENCES product_variants(product_variant_id);
UPDATE product_cost_history t SET product_variant_id = pv.product_variant_id
    FROM product_variants pv WHERE pv.product_id = t.product_id AND pv.is_default = TRUE;
CREATE INDEX idx_product_cost_history_variant ON product_cost_history(product_variant_id);
