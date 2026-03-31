-- =============================================================================
-- CONSOLIDATED SALES TRACKING
-- Consolidates V14 drink sales tracking and reorder calculations
-- =============================================================================

-- Daily sales aggregates
create table if not exists drink_sales_daily (
    id bigserial primary key,
    drink_id bigint not null references drinks(id),
    drink_variant_id bigint references drink_variants(id),
    sale_date date not null,
    quantity_sold numeric(12,2) not null default 0,
    volume_sold_ml numeric(14,2) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(drink_id, drink_variant_id, sale_date)
);

create index idx_drink_sales_daily_drink_id on drink_sales_daily(drink_id);
create index idx_drink_sales_daily_variant_id on drink_sales_daily(drink_variant_id);
create index idx_drink_sales_daily_date on drink_sales_daily(sale_date);

-- Weekly sales aggregates
create table if not exists drink_sales_weekly (
    id bigserial primary key,
    drink_id bigint not null references drinks(id),
    drink_variant_id bigint references drink_variants(id),
    week_start_date date not null,
    quantity_sold numeric(12,2) not null default 0,
    volume_sold_ml numeric(14,2) not null default 0,
    average_daily_quantity numeric(12,4) not null default 0,
    average_daily_volume_ml numeric(14,4) not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(drink_id, drink_variant_id, week_start_date)
);

create index idx_drink_sales_weekly_drink_id on drink_sales_weekly(drink_id);
create index idx_drink_sales_weekly_variant_id on drink_sales_weekly(drink_variant_id);
create index idx_drink_sales_weekly_week_start on drink_sales_weekly(week_start_date);

-- Reorder calculations (with corrected updated_at column)
create table if not exists reorder_calculations (
    id bigserial primary key,
    inventory_item_id bigint not null references inventory_items(id),
    calculation_date timestamptz not null,
    current_stock_amount numeric(14,2) not null,
    weekly_average_consumption numeric(12,4) not null,
    recommended_reorder_amount numeric(14,2) not null,
    is_below_threshold boolean not null,
    weeks_until_stockout numeric(6,2),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_reorder_calculations_inventory_item on reorder_calculations(inventory_item_id);
create index idx_reorder_calculations_date on reorder_calculations(calculation_date desc);

-- Consumption metadata (Lead-Time, Safety-Stock-Faktor)
create table if not exists consumption_metadata (
    id bigserial primary key,
    inventory_item_id bigint not null unique references inventory_items(id),
    lead_time_days integer not null default 3,
    safety_stock_factor numeric(4,2) not null default 1.5,
    weeks_lookback integer not null default 4,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_consumption_metadata_inventory_item on consumption_metadata(inventory_item_id);

