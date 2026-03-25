-- Dieses Script zeigt die Struktur der Sales Tracking Tabellen
-- und kann zum Testen der Nachbestellberechnung verwendet werden

-- 1. Tägliche Verkäufe einsehen
SELECT
  dsd.id,
  d.name as drink_name,
  dv.display_volume_name,
  dsd.sale_date,
  dsd.quantity_sold,
  dsd.volume_sold_ml
FROM drink_sales_daily dsd
JOIN drinks d ON dsd.drink_id = d.id
LEFT JOIN drink_variants dv ON dsd.drink_variant_id = dv.id
ORDER BY dsd.sale_date DESC
LIMIT 20;

-- 2. Wöchentliche Aggregation einsehen
SELECT
  dsw.id,
  d.name as drink_name,
  dv.display_volume_name,
  dsw.week_start_date,
  dsw.quantity_sold,
  dsw.volume_sold_ml,
  dsw.average_daily_quantity,
  dsw.average_daily_volume_ml
FROM drink_sales_weekly dsw
JOIN drinks d ON dsw.drink_id = d.id
LEFT JOIN drink_variants dv ON dsw.drink_variant_id = dv.id
ORDER BY dsw.week_start_date DESC
LIMIT 20;

-- 3. Nachbestellberechnungen einsehen
SELECT
  rc.id,
  ii.name as inventory_item_name,
  rc.calculation_date,
  rc.current_stock_amount,
  rc.weekly_average_consumption,
  rc.recommended_reorder_amount,
  rc.is_below_threshold,
  rc.weeks_until_stockout
FROM reorder_calculations rc
JOIN inventory_items ii ON rc.inventory_item_id = ii.id
ORDER BY rc.calculation_date DESC
LIMIT 20;

-- 4. Artikel unterhalb des Schwellwerts
SELECT
  rc.id,
  ii.name as inventory_item_name,
  ii.total_stock_amount,
  ii.reorder_threshold,
  rc.recommended_reorder_amount,
  rc.calculation_date
FROM reorder_calculations rc
JOIN inventory_items ii ON rc.inventory_item_id = ii.id
WHERE rc.is_below_threshold = true
ORDER BY rc.calculation_date DESC;

-- 5. Konsumptions-Metadaten einsehen
SELECT
  cm.id,
  ii.name as inventory_item_name,
  cm.lead_time_days,
  cm.safety_stock_factor,
  cm.weeks_lookback
FROM consumption_metadata cm
JOIN inventory_items ii ON cm.inventory_item_id = ii.id;

-- 6. Verbrauchstrend für ein spezifisches Getränk (letzte 8 Wochen)
SELECT
  dsw.week_start_date,
  dv.display_volume_name,
  dsw.volume_sold_ml,
  dsw.average_daily_volume_ml
FROM drink_sales_weekly dsw
JOIN drink_variants dv ON dsw.drink_variant_id = dv.id
WHERE dv.id = 12  -- Hier die Varianten-ID einfügen
ORDER BY dsw.week_start_date DESC
LIMIT 8;

-- 7. Manuelle Konsumptions-Metadaten hinzufügen
-- INSERT INTO consumption_metadata (inventory_item_id, lead_time_days, safety_stock_factor, weeks_lookback, created_at, updated_at)
-- VALUES (15, 5, 2.0, 8, NOW(), NOW());

-- 8. Test: Verkauf manuell erfassen (für Testing)
-- INSERT INTO drink_sales_daily (drink_id, drink_variant_id, sale_date, quantity_sold, volume_sold_ml, created_at, updated_at)
-- VALUES (5, 12, CURRENT_DATE, 1, 500, NOW(), NOW());

