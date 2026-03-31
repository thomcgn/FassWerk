-- =============================================================================
-- REORDER ORDERS TRACKING
-- Tracks planned deliveries with scheduled date/time and supplier
-- =============================================================================

CREATE TABLE IF NOT EXISTS suppliers (
    id bigserial primary key,
    name varchar(180) not null unique,
    contact_email varchar(255),
    contact_phone varchar(80),
    website varchar(500),
    notes text,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE TABLE IF NOT EXISTS reorder_orders (
    id bigserial primary key,
    inventory_item_id bigint not null references inventory_items(id),
    supplier_id bigint not null references suppliers(id),
    ordered_quantity numeric(14,2) not null,
    ordered_unit varchar(32) not null,
    scheduled_delivery_date date not null,
    scheduled_delivery_time time,
    status varchar(32) not null default 'PENDING',
    notes text,
    received_quantity numeric(14,2),
    received_at timestamptz,
    created_by varchar(120),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

CREATE INDEX IF NOT EXISTS idx_reorder_orders_inventory ON reorder_orders(inventory_item_id);
CREATE INDEX IF NOT EXISTS idx_reorder_orders_supplier ON reorder_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_reorder_orders_date ON reorder_orders(scheduled_delivery_date, status);
CREATE INDEX IF NOT EXISTS idx_reorder_orders_status ON reorder_orders(status);


