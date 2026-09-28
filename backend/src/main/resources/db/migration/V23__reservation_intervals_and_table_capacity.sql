-- Unknown capacities remain NULL: the operator must configure real seats before new bookings.
alter table tables add column seats integer check (seats > 0);
alter table reservations add column duration_minutes integer check (duration_minutes > 0);
alter table reservations add column starts_at timestamptz;
alter table reservations add column ends_at timestamptz;
alter table reservations add column check_in_deadline timestamptz;
alter table reservations add column business_date date;
alter table reservations add column reservation_zone varchar(80);
alter table reservations add constraint ck_reservation_interval
    check ((starts_at is null and ends_at is null) or (starts_at is not null and ends_at is not null and ends_at > starts_at));

create table reservation_tables (
    reservation_id bigint not null references reservations(id) on delete cascade,
    table_id bigint not null references tables(id),
    primary key (reservation_id, table_id)
);
create index idx_reservation_tables_table on reservation_tables(table_id);
create index idx_reservation_active_interval on reservations(status, starts_at, ends_at);
insert into reservation_tables(reservation_id,table_id)
select id,assigned_table_id from reservations where assigned_table_id is not null;
