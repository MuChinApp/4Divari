-- 0004: engagement — favorites, saved searches, requirements, matches, leads

create table favorites (
  user_id uuid not null references users(id) on delete cascade,
  listing_id uuid not null references listings(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, listing_id)
);

create index idx_favorites_listing on favorites (listing_id);

create table saved_searches (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  name text check (name is null or char_length(name) between 1 and 120),
  query jsonb not null,
  notify_new_listing boolean not null default true,
  notify_price_drop boolean not null default true,
  last_notified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger saved_searches_set_updated_at
  before update on saved_searches
  for each row execute function public.set_updated_at();

create index idx_saved_searches_user on saved_searches (user_id);

create table search_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id) on delete set null,
  session_id text,
  query text,
  filters jsonb,
  result_count int check (result_count is null or result_count >= 0),
  created_at timestamptz not null default now()
);

create index idx_search_events_user_time on search_events (user_id, created_at desc);

create table buyer_requirements (
  id uuid primary key default gen_random_uuid(),
  buyer_id uuid not null references users(id) on delete cascade,
  deal_type deal_type not null,
  budget_min_rial bigint check (budget_min_rial is null or budget_min_rial >= 0),
  budget_max_rial bigint check (budget_max_rial is null or budget_max_rial >= 0),
  area_min int check (area_min is null or area_min > 0),
  area_max int check (area_max is null or area_max > 0),
  bedrooms int[],
  cities text[],
  districts text[],
  features jsonb,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint requirements_budget_order check (
    budget_min_rial is null or budget_max_rial is null
    or budget_min_rial <= budget_max_rial
  )
);

create trigger buyer_requirements_set_updated_at
  before update on buyer_requirements
  for each row execute function public.set_updated_at();

create index idx_requirements_active on buyer_requirements (buyer_id) where is_active;

create table matches (
  id uuid primary key default gen_random_uuid(),
  requirement_id uuid references buyer_requirements(id) on delete cascade,
  listing_id uuid not null references listings(id) on delete cascade,
  score numeric(5,2) not null check (score >= 0 and score <= 100),
  explanation jsonb,
  created_at timestamptz not null default now(),
  unique (requirement_id, listing_id)
);

create index idx_matches_listing_score on matches (listing_id, score desc);

create type lead_stage as enum (
  'NEW','CONTACTED','QUALIFIED','VISIT_REQUESTED','VISITED',
  'NEGOTIATING','OFFER','CLOSED','LOST'
);

create table leads (
  id uuid primary key default gen_random_uuid(),
  stage lead_stage not null default 'NEW',
  source text not null,
  buyer_id uuid references users(id),
  seller_id uuid references users(id),
  listing_id uuid references listings(id),
  agent_id uuid not null references users(id),
  notes text check (notes is null or char_length(notes) <= 4000),
  next_action_at timestamptz,
  priority int not null default 3 check (priority between 1 and 5),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger leads_set_updated_at
  before update on leads
  for each row execute function public.set_updated_at();

create index idx_leads_agent_stage on leads (agent_id, stage);
create index idx_leads_next_action on leads (next_action_at) where next_action_at is not null;
