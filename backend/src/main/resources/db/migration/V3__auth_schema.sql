create table if not exists app_users (
    id bigserial primary key,
    name varchar(160) not null,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    role varchar(32) not null,
    active boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

insert into app_users (name, email, password_hash, role, active)
values
    ('Admin User', 'admin@fasswerk.local', '$2b$10$ndaU3xOlzKDbH9LeOXxdd.I5SDDbW435qMesiPtXzABYLqMtUAfBO', 'ADMIN', true),
    ('Staff User', 'staff@fasswerk.local', '$2b$10$MutZQdRnO6ergJHIlgulveM1a3zF5z3BjtpXUx9YGvILl5RZZ9lwm', 'STAFF', true)
on conflict (email) do nothing;

