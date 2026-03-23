alter table refresh_tokens
    add column if not exists user_agent varchar(255),
    add column if not exists ip_address varchar(64),
    add column if not exists last_used_at timestamptz,
    add column if not exists revoked_reason varchar(64);

create index if not exists idx_refresh_tokens_last_used on refresh_tokens(last_used_at);

