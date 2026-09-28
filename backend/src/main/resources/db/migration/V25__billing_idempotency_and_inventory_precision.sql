-- Preserve sub-cent-independent inventory quantities such as one millilitre in litres.
alter table inventory_items alter column content_per_package type numeric(14,4);
alter table inventory_items alter column total_stock_amount type numeric(14,4);
alter table inventory_movements alter column amount type numeric(14,4);
alter table table_order_items alter column deducted_volume_ml type numeric(14,4);

alter table inventory_movements add column operation_key varchar(80);
create unique index uq_inventory_movements_operation_key
    on inventory_movements(operation_key) where operation_key is not null;

create table billing_operations (
    id bigserial primary key,
    operation_key varchar(80) not null unique,
    operation_type varchar(32) not null,
    order_id bigint not null references table_orders(id),
    request_fingerprint varchar(64) not null,
    result_order_id bigint references table_orders(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table table_order_items
    add constraint ck_table_order_item_quantity_positive check (quantity > 0) not valid,
    add constraint ck_table_order_item_money_nonnegative check (unit_price >= 0 and total_price >= 0) not valid,
    add constraint ck_table_order_item_volume_nonnegative check (deducted_volume_ml >= 0) not valid;
alter table inventory_items
    add constraint ck_inventory_total_nonnegative check (total_stock_amount >= 0) not valid;

