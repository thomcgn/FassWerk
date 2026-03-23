create table if not exists opening_hours (
    id bigserial primary key,
    weekday varchar(16) not null unique,
    open boolean not null,
    open_time time,
    close_time time,
    second_open_time time,
    second_close_time time,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists booking_slot_config (
    id bigserial primary key,
    slot_duration_minutes integer not null,
    max_reservation_duration_minutes integer not null,
    no_show_grace_period_minutes integer not null,
    booking_interval_mode varchar(16) not null,
    active boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists tables (
    id bigserial primary key,
    name varchar(128) not null unique,
    capacity integer not null,
    area varchar(128),
    status varchar(32) not null,
    active boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists reservations (
    id bigserial primary key,
    guest_name varchar(160) not null,
    contact_email varchar(255),
    contact_phone varchar(80),
    reservation_date date not null,
    reservation_time time not null,
    guest_count integer not null,
    status varchar(32) not null,
    qr_code_token varchar(128) not null unique,
    expires_at timestamp,
    checked_in_at timestamp,
    assigned_table_id bigint references tables(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists drink_categories (
    id bigserial primary key,
    name varchar(120) not null unique,
    sort_order integer not null,
    active boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists drinks (
    id bigserial primary key,
    category_id bigint not null references drink_categories(id),
    name varchar(160) not null,
    description text,
    image_url varchar(500),
    active boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists drink_variants (
    id bigserial primary key,
    drink_id bigint not null references drinks(id),
    display_volume_name varchar(120) not null,
    volume_ml integer not null,
    price numeric(10,2) not null,
    sku varchar(120),
    active boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists inventory_items (
    id bigserial primary key,
    name varchar(180) not null,
    linked_drink_id bigint references drinks(id),
    linked_drink_variant_id bigint references drink_variants(id),
    package_type varchar(32) not null,
    packages_in_stock numeric(12,2) not null,
    content_per_package numeric(12,2) not null,
    content_unit varchar(32) not null,
    total_stock_amount numeric(14,2) not null,
    reorder_threshold numeric(14,2) not null,
    minimum_stock numeric(14,2) not null,
    recommended_reorder_amount numeric(14,2) not null,
    supplier varchar(180),
    active boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists inventory_movements (
    id bigserial primary key,
    inventory_item_id bigint not null references inventory_items(id),
    movement_type varchar(32) not null,
    amount numeric(14,2) not null,
    unit varchar(32) not null,
    reason varchar(255) not null,
    reference_type varchar(32) not null,
    reference_id varchar(120) not null,
    created_by varchar(120),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists table_orders (
    id bigserial primary key,
    table_id bigint not null references tables(id),
    reservation_id bigint references reservations(id),
    status varchar(32) not null,
    opened_at timestamp not null,
    closed_at timestamp,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists table_order_items (
    id bigserial primary key,
    table_order_id bigint not null references table_orders(id) on delete cascade,
    drink_variant_id bigint not null references drink_variants(id),
    quantity integer not null,
    unit_price numeric(10,2) not null,
    total_price numeric(10,2) not null,
    deducted_volume_ml numeric(12,2) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_reservations_slot on reservations(reservation_date, reservation_time, status);
create index if not exists idx_inventory_linked_variant on inventory_items(linked_drink_variant_id);
create index if not exists idx_table_orders_status on table_orders(table_id, status);

