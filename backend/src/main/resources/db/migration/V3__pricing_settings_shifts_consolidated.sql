-- =============================================================================
-- CONSOLIDATED PRICING, SETTINGS & SHIFT MANAGEMENT
-- Consolidates V11 (volume prices), V13 (shift settlements), V15 (inventory business settings)
-- =============================================================================

-- Volume prices
create table if not exists volume_prices (
    id bigserial primary key,
    volume_ml integer not null unique,
    price numeric(10,2) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

insert into volume_prices (volume_ml, price, created_at, updated_at)
select source.volume_ml, source.price, now(), now()
from (
    select distinct on (dv.volume_ml)
        dv.volume_ml,
        dv.price
    from drink_variants dv
    where dv.active = true
    order by dv.volume_ml, dv.updated_at desc, dv.id desc
) source
on conflict (volume_ml)
do update set
    price = excluded.price,
    updated_at = now();

-- Shift settlements
create table if not exists shift_settlements (
    id bigserial primary key,
    settlement_date date not null unique,
    opening_cash numeric(12,2) not null,
    other_expenses numeric(12,2) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists shift_worker_entries (
    id bigserial primary key,
    settlement_id bigint not null references shift_settlements(id) on delete cascade,
    employee_name varchar(160) not null,
    shift_start time not null,
    shift_end time not null,
    hourly_wage numeric(10,2) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Inventory business settings
create table if not exists inventory_business_settings (
    id bigserial primary key,
    business_timezone varchar(80) not null,
    business_day_ends_at time not null,
    manual_business_date date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

insert into inventory_business_settings (business_timezone, business_day_ends_at, manual_business_date)
select 'Europe/Berlin', '05:00:00', null
where not exists (select 1 from inventory_business_settings);

