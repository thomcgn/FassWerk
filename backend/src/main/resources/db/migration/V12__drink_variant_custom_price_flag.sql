alter table drink_variants
    add column if not exists use_volume_standard_price boolean not null default true;

update drink_variants
set use_volume_standard_price = true
where use_volume_standard_price is null;

