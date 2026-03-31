-- =============================================================================
-- DRINK VARIANT CUSTOM PRICE FLAG
-- =============================================================================

-- Column already created in V1, ensure all records have proper default
update drink_variants
set use_volume_standard_price = true
where use_volume_standard_price is null;

