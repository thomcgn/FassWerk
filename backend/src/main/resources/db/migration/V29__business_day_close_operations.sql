create table business_day_close_operations (
    operation_key varchar(80) primary key,
    business_date date not null,
    created_at timestamp with time zone not null default current_timestamp
);
