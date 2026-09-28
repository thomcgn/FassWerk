alter table inventory_items add column revision bigint not null default 0;
alter table shift_settlements add column revision bigint not null default 0;
