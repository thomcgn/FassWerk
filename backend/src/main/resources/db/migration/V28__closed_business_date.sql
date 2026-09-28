-- New closures capture the configured business date. Legacy timestamps remain
-- unchanged: their original Java/JDBC timezone cannot be inferred by SQL.
alter table table_orders add column closed_business_date date;
create index ix_order_business_date on table_orders(closed_business_date) where status='CLOSED' and paid;
