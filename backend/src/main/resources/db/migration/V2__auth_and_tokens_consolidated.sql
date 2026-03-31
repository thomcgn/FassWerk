-- =============================================================================
-- CONSOLIDATED AUTH & TOKENS
-- Consolidates V2 (seed core data), V3 (auth schema), V4 (refresh tokens), V5 (revoked access tokens), V6 (session metadata)
-- =============================================================================

-- Core booking configuration
insert into booking_slot_config (slot_duration_minutes, max_reservation_duration_minutes, no_show_grace_period_minutes, booking_interval_mode, active)
values (30, 180, 15, 'FIXED', true)
on conflict do nothing;

-- Opening hours defaults
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

-- Auth users table
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

-- Default users
insert into app_users (name, email, password_hash, role, active)
values
    ('Admin User', 'admin@fasswerk.local', '$2b$10$ndaU3xOlzKDbH9LeOXxdd.I5SDDbW435qMesiPtXzABYLqMtUAfBO', 'ADMIN', true),
    ('Staff User', 'staff@fasswerk.local', '$2b$10$MutZQdRnO6ergJHIlgulveM1a3zF5z3BjtpXUx9YGvILl5RZZ9lwm', 'STAFF', true)
on conflict (email) do nothing;

-- Refresh tokens
create table if not exists refresh_tokens (
    id bigserial primary key,
    token_id varchar(64) not null unique,
    user_id bigint not null references app_users(id),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    user_agent varchar(255),
    ip_address varchar(64),
    last_used_at timestamptz,
    revoked_reason varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_refresh_tokens_user on refresh_tokens(user_id);
create index if not exists idx_refresh_tokens_revoked on refresh_tokens(revoked_at);
create index if not exists idx_refresh_tokens_last_used on refresh_tokens(last_used_at);

-- Revoked access tokens
create table if not exists revoked_access_tokens (
    id bigserial primary key,
    token_id varchar(64) not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_revoked_access_tokens_expires_at on revoked_access_tokens(expires_at);

