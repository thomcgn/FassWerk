create table if not exists refresh_tokens (
    id bigserial primary key,
    token_id varchar(64) not null unique,
    user_id bigint not null references app_users(id),
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_refresh_tokens_user on refresh_tokens(user_id);
create index if not exists idx_refresh_tokens_revoked on refresh_tokens(revoked_at);

