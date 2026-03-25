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

