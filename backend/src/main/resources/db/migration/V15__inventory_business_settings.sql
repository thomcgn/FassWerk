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

