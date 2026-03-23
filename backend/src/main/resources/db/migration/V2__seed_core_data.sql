insert into booking_slot_config (slot_duration_minutes, max_reservation_duration_minutes, no_show_grace_period_minutes, booking_interval_mode, active)
values (30, 180, 15, 'FIXED', true);

insert into opening_hours (weekday, open, open_time, close_time, second_open_time, second_close_time)
values
    ('MONDAY', true, '17:00', '23:00', null, null),
    ('TUESDAY', true, '17:00', '23:00', null, null),
    ('WEDNESDAY', true, '17:00', '23:00', null, null),
    ('THURSDAY', true, '17:00', '23:00', null, null),
    ('FRIDAY', true, '16:00', '23:59', null, null),
    ('SATURDAY', true, '14:00', '23:59', null, null),
    ('SUNDAY', true, '14:00', '22:00', null, null)
on conflict (weekday) do nothing;

insert into tables (name, capacity, area, status, active)
values
    ('T1', 4, 'INSIDE', 'FREE', true),
    ('T2', 4, 'INSIDE', 'FREE', true),
    ('T3', 6, 'OUTSIDE', 'FREE', true),
    ('BAR1', 2, 'BAR', 'FREE', true)
on conflict (name) do nothing;

insert into drink_categories (name, sort_order, active)
values
    ('Beer', 10, true),
    ('Softdrinks', 20, true)
on conflict (name) do nothing;

insert into drinks (category_id, name, description, image_url, active)
select c.id, 'Guinness', 'Irish stout', null, true
from drink_categories c
where c.name = 'Beer'
  and not exists (select 1 from drinks d where d.name = 'Guinness');

insert into drinks (category_id, name, description, image_url, active)
select c.id, 'Tonic Water', 'Classic tonic', null, true
from drink_categories c
where c.name = 'Softdrinks'
  and not exists (select 1 from drinks d where d.name = 'Tonic Water');

insert into drink_variants (drink_id, display_volume_name, volume_ml, price, sku, active)
select d.id, 'Pint', 500, 5.90, 'GUI-PINT', true
from drinks d
where d.name = 'Guinness'
  and not exists (select 1 from drink_variants dv where dv.sku = 'GUI-PINT');

insert into drink_variants (drink_id, display_volume_name, volume_ml, price, sku, active)
select d.id, 'Bottle', 200, 2.80, 'TON-BOT', true
from drinks d
where d.name = 'Tonic Water'
  and not exists (select 1 from drink_variants dv where dv.sku = 'TON-BOT');

insert into inventory_items (
    name,
    linked_drink_id,
    linked_drink_variant_id,
    package_type,
    packages_in_stock,
    content_per_package,
    content_unit,
    total_stock_amount,
    reorder_threshold,
    minimum_stock,
    recommended_reorder_amount,
    supplier,
    active
)
select
    'Guinness Keg Stock',
    d.id,
    v.id,
    'BARREL',
    3,
    30000,
    'MILLILITER',
    90000,
    30000,
    20000,
    60000,
    'Dublin Beverages',
    true
from drinks d
join drink_variants v on v.drink_id = d.id and v.sku = 'GUI-PINT'
where d.name = 'Guinness'
  and not exists (select 1 from inventory_items i where i.name = 'Guinness Keg Stock');

insert into inventory_items (
    name,
    linked_drink_id,
    linked_drink_variant_id,
    package_type,
    packages_in_stock,
    content_per_package,
    content_unit,
    total_stock_amount,
    reorder_threshold,
    minimum_stock,
    recommended_reorder_amount,
    supplier,
    active
)
select
    'Tonic Bottle Stock',
    d.id,
    v.id,
    'BOX',
    5,
    4800,
    'MILLILITER',
    24000,
    8000,
    5000,
    12000,
    'Beverage Supply GmbH',
    true
from drinks d
join drink_variants v on v.drink_id = d.id and v.sku = 'TON-BOT'
where d.name = 'Tonic Water'
  and not exists (select 1 from inventory_items i where i.name = 'Tonic Bottle Stock');


