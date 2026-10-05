# 4Divari — Database Schema (Phase 2 blueprint)

Target: **PostgreSQL (Supabase)** with Row Level Security on every table.  
Backend language-neutral: snake_case columns, UTC timestamps (`timestamptz`), UUID PKs (`gen_random_uuid()`), Persian text stored as-is (no language column needed for content).

---

## Conventions

- **PK:** `id uuid primary key default gen_random_uuid()`
- **Timestamps:** `created_at timestamptz not null default now()`, `updated_at` maintained by trigger
- **Soft delete:** `deleted_at timestamptz` where history matters (properties, listings, users)
- **Enums:** Postgres `enum` types for status fields (extensible via `ALTER TYPE`)
- **Indexes:** FKs indexed; search vectors and freshest-listing indexes per query patterns
- **RLS:** enabled on every table; policies by role via `user_roles`

---

## Identity & roles

```sql
create type user_status as enum ('pending', 'active', 'suspended', 'deleted');

create table users (
  id uuid primary key default gen_random_uuid(),
  phone_e164 text not null unique,
  phone_verified_at timestamptz,
  status user_status not null default 'pending',
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table profiles (
  user_id uuid primary key references users(id) on delete cascade,
  display_name text,
  bio text,
  avatar_path text,
  locale text not null default 'fa_IR',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create type role_code as enum (
  'GUEST','BUYER','TENANT','SELLER','LANDLORD',
  'AGENT','AGENCY_OWNER','AGENCY_STAFF','ADMIN','VERIFIED_PROFESSIONAL'
);

create table roles (
  code role_code primary key,
  description text
);

-- Multi-role: NEVER user.role = 'AGENT'
create table user_roles (
  user_id uuid not null references users(id) on delete cascade,
  role role_code not null references roles(code),
  granted_at timestamptz not null default now(),
  granted_by uuid references users(id),
  primary key (user_id, role)
);

create table permissions (
  code text primary key,
  description text
);

create table role_permissions (
  role role_code not null references roles(code) on delete cascade,
  permission text not null references permissions(code) on delete cascade,
  primary key (role, permission)
);

create table agencies (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  owner_id uuid not null references users(id),
  phone_e164 text,
  city text,
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table agency_members (
  agency_id uuid not null references agencies(id) on delete cascade,
  user_id uuid not null references users(id) on delete cascade,
  member_role text not null check (member_role in ('owner','manager','staff')),
  joined_at timestamptz not null default now(),
  primary key (agency_id, user_id)
);
```

---

## Property domain

```sql
create type property_type as enum (
  'APARTMENT','HOUSE','VILLA','LAND','SHOP','OFFICE','INDUSTRIAL','OTHER'
);

create table properties (
  id uuid primary key default gen_random_uuid(),
  property_type property_type not null,
  area_sqm int not null check (area_sqm > 0),
  built_area_sqm int check (built_area_sqm > 0),
  land_sqm int,
  bedrooms int not null default 0 check (bedrooms >= 0),
  floor int,
  total_floors int,
  build_year int check (build_year between 1200 and 1500),
  has_elevator boolean not null default false,
  has_parking boolean not null default false,
  has_storage boolean not null default false,
  has_balcony boolean not null default false,
  orientation text,
  renovation_status text,
  heating text,
  cooling text,
  deed_status text,
  ownership_count int,
  occupancy_status text,
  has_tenant boolean not null default false,
  description text,
  latitude double precision,
  longitude double precision,
  neighborhood text,
  city text not null,
  province text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index idx_properties_city_area on properties (city, area_sqm) where deleted_at is null;
create index idx_properties_geo on properties (latitude, longitude) where deleted_at is null;

create table property_addresses (
  property_id uuid primary key references properties(id) on delete cascade,
  province text not null,
  city text not null,
  district text,
  neighborhood text,
  street text,
  alley text,
  unit text,
  postal_code text,
  raw_text text
);

create table property_media (
  id uuid primary key default gen_random_uuid(),
  property_id uuid not null references properties(id) on delete cascade,
  storage_path text not null,
  media_type text not null check (media_type in ('image','video','floor_plan','document')),
  mime_type text not null,
  byte_size int not null check (byte_size > 0),
  width int,
  height int,
  sort_order int not null default 0,
  is_cover boolean not null default false,
  blurhash text,
  created_at timestamptz not null default now()
);
create index idx_media_property on property_media (property_id, sort_order);

create table property_features (
  property_id uuid not null references properties(id) on delete cascade,
  feature_key text not null,
  feature_value text,
  primary key (property_id, feature_key)
);
```

---

## Listing (separate from Property)

```sql
create type listing_status as enum (
  'DRAFT','PENDING_VERIFICATION','ACTIVE','PAUSED',
  'UNDER_OFFER','SOLD','RENTED','EXPIRED','REJECTED'
);

create type deal_type as enum ('SALE','RENT','RENT_WITH_DEPOSIT');

create table listings (
  id uuid primary key default gen_random_uuid(),
  property_id uuid not null references properties(id),
  seller_id uuid references users(id),
  agent_id uuid references users(id),
  agency_id uuid references agencies(id),
  deal_type deal_type not null,
  price_rial bigint not null check (price_rial > 0),
  deposit_rial bigint check (deposit_rial >= 0),
  rent_rial bigint check (rent_rial >= 0),
  price_per_sqm_rial bigint generated always as
    (price_rial / nullif(area_sqm, 0)) stored,  -- needs area join; prefer view
  terms text,
  status listing_status not null default 'DRAFT',
  published_at timestamptz,
  expires_at timestamptz,
  last_verified_at timestamptz not null default now(),
  last_updated_at timestamptz not null default now(),
  verification_status text not null default 'unverified',
  freshness text not null default 'fresh' check (freshness in ('fresh','aging','stale')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint listings_one_party check (
    seller_id is not null or agent_id is not null
  )
);
create index idx_listings_status_published on listings (status, published_at desc);
create index idx_listings_agent on listings (agent_id) where deleted_at is null;
create index idx_listings_freshness on listings (freshness, last_verified_at);

create table listing_status_history (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id) on delete cascade,
  from_status listing_status,
  to_status listing_status not null,
  changed_by uuid references users(id),
  reason text,
  created_at timestamptz not null default now()
);

create table property_verifications (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id) on delete cascade,
  kind text not null check (kind in (
    'owner','phone','location','agent','completeness','document'
  )),
  status text not null check (status in ('pending','passed','failed','expired')),
  evidence_path text,
  reviewed_by uuid references users(id),
  verified_at timestamptz,
  expires_at timestamptz,
  created_at timestamptz not null default now()
);

create table property_price_history (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id) on delete cascade,
  price_rial bigint not null,
  deposit_rial bigint,
  rent_rial bigint,
  changed_at timestamptz not null default now(),
  changed_by uuid references users(id)
);
```

---

## Marketplace engagement

```sql
create table favorites (
  user_id uuid not null references users(id) on delete cascade,
  listing_id uuid not null references listings(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, listing_id)
);

create table saved_searches (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  name text,
  query jsonb not null,
  notify_new_listing boolean not null default true,
  notify_price_drop boolean not null default true,
  last_notified_at timestamptz,
  created_at timestamptz not null default now()
);

create table search_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id),
  session_id text,
  query text,
  filters jsonb,
  result_count int,
  created_at timestamptz not null default now()
);

create table buyer_requirements (
  id uuid primary key default gen_random_uuid(),
  buyer_id uuid not null references users(id) on delete cascade,
  deal_type deal_type not null,
  budget_min_rial bigint,
  budget_max_rial bigint,
  area_min int,
  area_max int,
  bedrooms int[],
  cities text[],
  districts text[],
  features jsonb,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table matches (
  id uuid primary key default gen_random_uuid(),
  requirement_id uuid references buyer_requirements(id) on delete cascade,
  listing_id uuid not null references listings(id) on delete cascade,
  score numeric(5,2) not null check (score between 0 and 100),
  explanation jsonb,
  created_at timestamptz not null default now(),
  unique (requirement_id, listing_id)
);

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
  notes text,
  next_action_at timestamptz,
  priority int not null default 3 check (priority between 1 and 5),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index idx_leads_agent_stage on leads (agent_id, stage);
```

---

## Communication & transactions

```sql
create table conversations (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid references listings(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table conversation_participants (
  conversation_id uuid not null references conversations(id) on delete cascade,
  user_id uuid not null references users(id) on delete cascade,
  last_read_at timestamptz,
  primary key (conversation_id, user_id)
);

create table messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references conversations(id) on delete cascade,
  sender_id uuid not null references users(id),
  body text,
  attachment_path text,
  sent_at timestamptz not null default now(),
  deleted_at timestamptz
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
-- Double-booking prevention (partial unique):
create unique index idx_visits_no_double_book
  on visits (listing_id, slot_start)
  where status in ('REQUESTED','CONFIRMED');

create type offer_status as enum (
  'PENDING','ACCEPTED','REJECTED','COUNTERED','WITHDRAWN','EXPIRED'
);

create table offers (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id),
  buyer_id uuid not null references users(id),
  amount_rial bigint not null check (amount_rial > 0),
  message text,
  status offer_status not null default 'PENDING',
  parent_offer_id uuid references offers(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table offer_events (
  id uuid primary key default gen_random_uuid(),
  offer_id uuid not null references offers(id) on delete cascade,
  actor_id uuid not null references users(id),
  event text not null,
  payload jsonb,
  created_at timestamptz not null default now()
);
```

---

## Trust, notifications, audit

```sql
create table notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  type text not null,
  title text not null,
  body text,
  data jsonb,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create table reports (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid references listings(id),
  reporter_id uuid references users(id),
  reason text not null,
  detail text,
  status text not null default 'open' check (status in ('open','reviewing','actioned','dismissed')),
  reviewed_by uuid references users(id),
  created_at timestamptz not null default now()
);

create table reviews (
  id uuid primary key default gen_random_uuid(),
  agent_id uuid not null references users(id),
  author_id uuid not null references users(id),
  listing_id uuid references listings(id),
  rating int not null check (rating between 1 and 5),
  body text,
  created_at timestamptz not null default now()
);

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
```

---

## RLS sketch (applied in Phase 2 migrations)

```sql
alter table listings enable row level security;
-- Public read of ACTIVE listings
create policy listings_public_read on listings
  for select using (status = 'ACTIVE' and deleted_at is null);
-- Owner/agent full read of own rows
create policy listings_owner_all on listings
  for all using (
    seller_id = auth.uid() OR agent_id = auth.uid()
    OR exists (
      select 1 from agency_members m
      where m.agency_id = listings.agency_id and m.user_id = auth.uid()
    )
  ) with check (true);
-- Writes for non-ACTIVE transitions go through SECURITY DEFINER RPCs (Phase 2)
```

Same pattern for `favorites` (owner-only), `messages` (participants-only), `leads` (agent-only), `audit_logs` (admin insert-only via service role).

---

## Anti-fraud hooks (schema-ready, logic Phase 7)

- `listings.last_verified_at` + `freshness` → staleness notifications  
- Unique `phone_e164` on users → repeated-phone detection  
- `property_media.storage_path` hash column (add later) → duplicate image detection  
- `reports` + moderation status → fake listing pipeline  

*This schema is the contract. Migrations land in Phase 2 before complex UI.*
