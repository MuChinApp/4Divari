-- 0006: notifications, reports, reviews, audit_logs

create table notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  type text not null,
  title text not null check (char_length(title) between 1 and 200),
  body text check (body is null or char_length(body) <= 2000),
  data jsonb,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create index idx_notifications_user_unread
  on notifications (user_id, created_at desc)
  where read_at is null;

create table reports (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid references listings(id) on delete set null,
  reporter_id uuid references users(id) on delete set null,
  reason text not null check (char_length(reason) between 3 and 500),
  detail text check (detail is null or char_length(detail) <= 4000),
  status text not null default 'open'
    check (status in ('open','reviewing','actioned','dismissed')),
  reviewed_by uuid references users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger reports_set_updated_at
  before update on reports
  for each row execute function public.set_updated_at();

create index idx_reports_status on reports (status, created_at desc);

create table reviews (
  id uuid primary key default gen_random_uuid(),
  agent_id uuid not null references users(id) on delete cascade,
  author_id uuid not null references users(id) on delete cascade,
  listing_id uuid references listings(id) on delete set null,
  rating int not null check (rating between 1 and 5),
  body text check (body is null or char_length(body) <= 4000),
  created_at timestamptz not null default now()
);

create unique index idx_reviews_one_per_context
  on reviews (agent_id, author_id, coalesce(listing_id, '00000000-0000-0000-0000-000000000000'::uuid));

create table audit_logs (
  id bigserial primary key,
  actor_id uuid references users(id),
  action text not null,
  entity_type text not null,
  entity_id text not null,
  before jsonb,
  after jsonb,
  ip inet,
  user_agent text,
  created_at timestamptz not null default now()
);

create index idx_audit_entity on audit_logs (entity_type, entity_id, created_at desc);
create index idx_audit_actor_time on audit_logs (actor_id, created_at desc);

-- Append-only for normal roles
create or replace function public.block_audit_mutation()
returns trigger
language plpgsql
as $$
begin
  raise exception 'audit_logs is append-only';
end;
$$;

create trigger audit_logs_no_update
  before update or delete on audit_logs
  for each row execute function public.block_audit_mutation();

-- Verification changes always leave an audit trail
create or replace function public.audit_verification_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into audit_logs (actor_id, action, entity_type, entity_id, before, after)
  values (
    coalesce(new.reviewed_by, public.current_user_id()),
    'verification.' || coalesce(new.status, 'unknown'),
    'property_verifications',
    new.id::text,
    case when tg_op = 'UPDATE'
      then jsonb_build_object('status', old.status)
      else null
    end,
    jsonb_build_object('status', new.status, 'kind', new.kind)
  );
  return new;
end;
$$;

create trigger property_verifications_audit
  after insert or update on property_verifications
  for each row execute function public.audit_verification_change();
