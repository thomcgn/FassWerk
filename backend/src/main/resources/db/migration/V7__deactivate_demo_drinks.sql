-- Deactivate historical demo beverages so new installs and existing systems start without demo products.

update inventory_items
set active = false,
    updated_at = now()
where name in ('Guinness Keg Stock', 'Tonic Bottle Stock')
   or linked_drink_variant_id in (
       select id
       from drink_variants
       where sku in ('GUI-PINT', 'TON-BOT')
   )
   or linked_drink_id in (
       select id
       from drinks
       where name in ('Guinness', 'Tonic Water')
   );

update drink_variants
set active = false,
    updated_at = now()
where sku in ('GUI-PINT', 'TON-BOT');

update drinks
set active = false,
    updated_at = now()
where name in ('Guinness', 'Tonic Water');

