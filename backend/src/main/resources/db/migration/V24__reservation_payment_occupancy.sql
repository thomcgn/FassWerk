-- Schema-only change: retain every historical reservation and billing value.
-- NULL end now means no planned stay limit.
alter table reservations drop constraint ck_reservation_interval;
alter table reservations add constraint ck_reservation_interval
    check (ends_at is null or (starts_at is not null and ends_at > starts_at));

-- Existing duplicate OPEN bills stop migration for operator review; no records are deleted.
create unique index uq_table_orders_one_open_per_table on table_orders(table_id) where status = 'OPEN';
create index idx_table_orders_reservation on table_orders(reservation_id);
