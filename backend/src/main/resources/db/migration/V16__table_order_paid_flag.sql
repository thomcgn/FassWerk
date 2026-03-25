alter table table_orders
    add column if not exists paid boolean not null default true;

update table_orders
set paid = true
where paid is null;

