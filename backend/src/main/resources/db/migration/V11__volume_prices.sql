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

