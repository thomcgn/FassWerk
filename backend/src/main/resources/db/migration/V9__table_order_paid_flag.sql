-- =============================================================================
-- TABLE ORDER PAID FLAG (Legacy - column now in consolidated V1)
-- =============================================================================

-- Column already created in V1, ensure all records have proper default
update table_orders
set paid = true
where paid is null;

