-- Audit writes are part of the original transaction; no foreign keys that could erase history.
create table business_audit_events (
    id bigserial primary key,
    occurred_at timestamptz not null default clock_timestamp(),
    transaction_id bigint not null default txid_current(),
    actor varchar(80) not null,
    request_id varchar(128),
    action varchar(64) not null,
    entity_type varchar(64) not null,
    entity_id varchar(80) not null,
    reason text not null,
    metadata jsonb not null
);
create index ix_business_audit_entity on business_audit_events(entity_type, entity_id, id);
create index ix_business_audit_time on business_audit_events(occurred_at, id);

create function business_audit_immutable() returns trigger language plpgsql as $$
begin
    raise exception 'Business audit history is append-only' using errcode = '23514';
end $$;
create trigger business_audit_immutable before update or delete or truncate on business_audit_events
    for each statement execute function business_audit_immutable();

-- Deliberate projections: no authentication credentials, guest contacts, or arbitrary HTTP payloads.
create function business_audit_projection(entity text, value jsonb) returns jsonb language sql immutable as $$
select case when value is null then null
 when entity = 'drink_variants' then jsonb_build_object(
    'id', value->'id', 'drink_id', value->'drink_id', 'volume_ml', value->'volume_ml',
    'price', value->'price', 'use_volume_standard_price', value->'use_volume_standard_price')
 else value - array['created_at','updated_at','created_by','operation_key','request_fingerprint'] end
$$;

create function business_audit_capture() returns trigger language plpgsql as $$
declare
    previous jsonb;
    current_value jsonb;
    event_action text;
    explanation text;
    event_id text;
begin
    if TG_OP <> 'INSERT' then previous := business_audit_projection(TG_TABLE_NAME, to_jsonb(old)); end if;
    if TG_OP <> 'DELETE' then
        current_value := business_audit_projection(TG_TABLE_NAME, to_jsonb(new));
        event_id := coalesce(to_jsonb(new)->>'id', encode(sha256(convert_to(to_jsonb(new)->>'operation_key', 'UTF8')), 'hex'));
    else
        event_id := coalesce(to_jsonb(old)->>'id', encode(sha256(convert_to(to_jsonb(old)->>'operation_key', 'UTF8')), 'hex'));
    end if;
    if previous is not distinct from current_value then return null; end if;
    event_action := case
      when TG_TABLE_NAME = 'table_orders' and TG_OP = 'INSERT' and (current_value->>'paid')::boolean then 'SPLIT_PAYMENT'
      when TG_TABLE_NAME = 'table_orders' and current_value->>'status' = 'CLOSED' and (current_value->>'paid')::boolean then 'PAYMENT_CLOSE'
      when TG_TABLE_NAME = 'table_orders' and current_value->>'status' = 'CLOSED' then 'ARCHIVE_UNPAID'
      when TG_TABLE_NAME = 'table_orders' and previous->>'status' = 'CLOSED' then 'REOPEN_UNPAID'
      when TG_TABLE_NAME = 'table_order_items' and (TG_OP = 'DELETE' or (current_value->>'quantity')::integer < (previous->>'quantity')::integer) then 'ITEM_REDUCTION'
      when TG_TABLE_NAME = 'inventory_movements' then 'INVENTORY_MOVEMENT'
      when TG_TABLE_NAME in ('drink_variants', 'volume_prices') then 'PRICE_' || TG_OP
      when TG_TABLE_NAME in ('shift_settlements', 'shift_worker_entries') then 'SHIFT_' || TG_OP
      when TG_TABLE_NAME = 'business_day_close_operations' then 'BUSINESS_DAY_CLOSE'
      else TG_TABLE_NAME || '_' || TG_OP end;
    explanation := coalesce(nullif(current_value->>'reason',''), 'Committed ' || event_action);
    insert into business_audit_events(actor, request_id, action, entity_type, entity_id, reason, metadata)
    values(coalesce(nullif(current_setting('fasswerk.audit_actor', true), ''), 'system:unattributed'),
      nullif(current_setting('fasswerk.audit_request', true), ''), event_action, TG_TABLE_NAME,
      event_id, explanation,
      jsonb_build_object('before', previous, 'after', current_value));
    return null;
end $$;

create trigger audit_table_orders after insert or update or delete on table_orders for each row execute function business_audit_capture();
create trigger audit_table_order_items after insert or update or delete on table_order_items for each row execute function business_audit_capture();
create trigger audit_billing_operations after insert or update or delete on billing_operations for each row execute function business_audit_capture();
create trigger audit_inventory_movements after insert or update or delete on inventory_movements for each row execute function business_audit_capture();
create trigger audit_drink_variants after insert or update or delete on drink_variants for each row execute function business_audit_capture();
create trigger audit_volume_prices after insert or update or delete on volume_prices for each row execute function business_audit_capture();
create trigger audit_shift_settlements after insert or update or delete on shift_settlements for each row execute function business_audit_capture();
create trigger audit_shift_worker_entries after insert or update or delete on shift_worker_entries for each row execute function business_audit_capture();
create trigger audit_day_close after insert or update or delete on business_day_close_operations for each row execute function business_audit_capture();
