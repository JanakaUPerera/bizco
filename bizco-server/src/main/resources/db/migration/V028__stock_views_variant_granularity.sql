-- Phase 6 / Week 18 (DatabaseDesign.md §56.3 Step 4, §56.4): repoints the three stock views onto
-- product_variant_id. `stock_movements`/`held_sale_items` are already dual-written by the
-- application (Week 18's Java-side repoint) -- this migration is the DB-side half of the same
-- cutover, run once the application no longer relies on the old product_id-keyed shape.
--
-- v_available_stock's `products.product_type = 'INVENTORY'` filter moves to a join through
-- product_variants.product_id = products.product_id, per §56.4 exactly.

DROP VIEW v_available_stock;
DROP VIEW v_stock_on_hand;
DROP VIEW v_reserved_stock;

CREATE VIEW v_reserved_stock AS
SELECT
    hsi.product_variant_id,
    COALESCE(SUM(hsi.quantity), 0)::NUMERIC(15,3) AS reserved_stock
FROM held_sale_items hsi
JOIN held_sales hs ON hs.held_sale_id = hsi.held_sale_id
WHERE hs.status IN ('HELD','RESUMED')
  AND hsi.product_variant_id IS NOT NULL
GROUP BY hsi.product_variant_id;

CREATE VIEW v_stock_on_hand AS
SELECT
    product_variant_id,
    COALESCE(SUM(quantity), 0)::NUMERIC(15,3) AS physical_stock
FROM stock_movements
WHERE product_variant_id IS NOT NULL
GROUP BY product_variant_id;

CREATE VIEW v_available_stock AS
SELECT
    pv.product_variant_id,
    COALESCE(soh.physical_stock, 0)::NUMERIC(15,3) AS physical_stock,
    COALESCE(rs.reserved_stock, 0)::NUMERIC(15,3) AS reserved_stock,
    (
      COALESCE(soh.physical_stock, 0)
      - COALESCE(rs.reserved_stock, 0)
    )::NUMERIC(15,3) AS available_stock
FROM product_variants pv
JOIN products p ON p.product_id = pv.product_id
LEFT JOIN v_stock_on_hand soh ON soh.product_variant_id = pv.product_variant_id
LEFT JOIN v_reserved_stock rs ON rs.product_variant_id = pv.product_variant_id
WHERE p.product_type = 'INVENTORY';
