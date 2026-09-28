-- One-time upgrade logout: historical refresh rotation families cannot be
-- reconstructed reliably. Require a new login under the session-family model.
-- Preserve the reason/time of sessions that were already revoked.
update refresh_tokens
set revoked_at = current_timestamp, revoked_reason = 'SECURITY_UPGRADE'
where revoked_at is null;

-- Existing access tokens without accessVersion are rejected by the new filter.
alter table app_users add column access_version bigint not null default 0;
alter table refresh_tokens add column family_id varchar(64);
update refresh_tokens set family_id = token_id;
alter table refresh_tokens alter column family_id set not null;
alter table refresh_tokens alter column family_id set default gen_random_uuid()::text;
alter table refresh_tokens add column browser_session boolean not null default false;
create index ix_refresh_family on refresh_tokens(user_id, family_id);
create table auth_rate_buckets (
    bucket_key varchar(64) not null,
    window_start timestamptz not null,
    attempts integer not null,
    primary key(bucket_key, window_start)
);
create index ix_auth_rate_expiry on auth_rate_buckets(window_start);
alter table inventory_business_settings add column weeks_lookback integer check(weeks_lookback between 1 and 104);
alter table inventory_business_settings add column default_safety_factor numeric(10,4) check(default_safety_factor between 0 and 100);
alter table inventory_business_settings add column default_lead_time_days integer check(default_lead_time_days between 0 and 365);
