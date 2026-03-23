create table if not exists revoked_access_tokens (
    id bigserial primary key,
    token_id varchar(64) not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_revoked_access_tokens_expires_at on revoked_access_tokens(expires_at);

