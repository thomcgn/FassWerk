-- Existing merged items have no reliable originating date. Keep NULL and require
-- explicit reconciliation rather than inventing a date for financial history.
alter table table_order_items add column sale_business_date date;
create index ix_order_item_sale_day on table_order_items(table_order_id, drink_variant_id, sale_business_date);
