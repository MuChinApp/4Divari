-- 0005: chat, visits (double-book guard), offers (audit trail)

create table conversations (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid references listings(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger conversations_set_updated_at
  before update on conversations
  for each row execute function public.set_updated_at();

create table conversation_participants (
  conversation_id uuid not null references conversations(id) on delete cascade,
  user_id uuid not null references users(id) on delete cascade,
  last_read_at timestamptz,
  joined_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

create index idx_participants_user on conversation_participants (user_id);

create table messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references conversations(id) on delete cascade,
  sender_id uuid not null references users(id),
  body text check (body is null or char_length(body) between 1 and 8000),
  attachment_path text,
  sent_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint messages_body_or_attachment check (body is not null or attachment_path is not null)
);

create index idx_messages_conversation on messages (conversation_id, sent_at);

create type visit_status as enum (
  'REQUESTED','CONFIRMED','RESCHEDULED','COMPLETED','CANCELLED'
);

create table visits (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id),
  buyer_id uuid not null references users(id),
  agent_id uuid references users(id),
  slot_start timestamptz not null,
  slot_end timestamptz not null,
  status visit_status not null default 'REQUESTED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (slot_end > slot_start)
);

create trigger visits_set_updated_at
  before update on visits
  for each row execute function public.set_updated_at();

-- Double booking prevention (backend guarantee, not client-side)
create unique index idx_visits_no_double_book
  on visits (listing_id, slot_start)
  where status in ('REQUESTED','CONFIRMED');

create index idx_visits_buyer on visits (buyer_id, slot_start);
create index idx_visits_agent on visits (agent_id, slot_start);

create type offer_status as enum (
  'PENDING','ACCEPTED','REJECTED','COUNTERED','WITHDRAWN','EXPIRED'
);

create table offers (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id),
  buyer_id uuid not null references users(id),
  amount_rial bigint not null check (amount_rial > 0),
  message text check (message is null or char_length(message) <= 4000),
  status offer_status not null default 'PENDING',
  parent_offer_id uuid references offers(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger offers_set_updated_at
  before update on offers
  for each row execute function public.set_updated_at();

create table offer_events (
  id uuid primary key default gen_random_uuid(),
  offer_id uuid not null references offers(id) on delete cascade,
  actor_id uuid not null references users(id),
  event text not null,
  payload jsonb,
  created_at timestamptz not null default now()
);

create index idx_offer_events_offer on offer_events (offer_id, created_at);

create or replace function public.log_offer_created()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into offer_events (offer_id, actor_id, event, payload)
  values (
    new.id,
    new.buyer_id,
    'CREATED',
    jsonb_build_object('amount_rial', new.amount_rial, 'status', new.status)
  );
  return new;
end;
$$;

create trigger offers_after_insert
  after insert on offers
  for each row execute function public.log_offer_created();
