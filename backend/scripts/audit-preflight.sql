-- Read-only checks for a restored V25 (or newer) database; never a repair script.
-- Execute against the disposable restore copy using psql -v ON_ERROR_STOP=1 -f ...
begin read only;
select installed_rank, version, description, checksum, success
from flyway_schema_history order by installed_rank;
select id, length(token_id) as token_id_length from refresh_tokens where length(token_id) > 64;
select table_id, count(*) as open_bills from table_orders
where status='OPEN' group by table_id having count(*) > 1;
select id, seats from tables where active and (seats is null or seats < 1);
select id, drink_id, sale_date, quantity_sold, volume_sold_ml
from drink_sales_daily where drink_variant_id is null;
select i.id, i.table_order_id, i.drink_variant_id, i.quantity, o.opened_at
from table_order_items i join table_orders o on o.id=i.table_order_id
where o.status='OPEN' order by o.id, i.id;
select id from table_order_items
where quantity < 1 or unit_price < 0 or total_price < 0 or deducted_volume_ml < 0;
select id from inventory_items where total_stock_amount < 0;
commit;
