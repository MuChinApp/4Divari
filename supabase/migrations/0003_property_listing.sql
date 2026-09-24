-- 0003: property domain + listings (Property ≠ Listing)

create type property_type as enum (
  'APARTMENT','HOUSE','VILLA','LAND','SHOP','OFFICE','INDUSTRIAL','OTHER'
);

create table properties (
  id uuid primary key default gen_random_uuid(),
  property_type property_type not null,
  area_sqm int not null check (area_sqm > 0),
  built_area_sqm int check (built_area_sqm > 0),
  land_sqm int check (land_sqm is null or land_sqm > 0),
  bedrooms int not null default 0 check (bedrooms >= 0),
  floor int check (floor is null or floor >= 0),
  total_floors int check (total_floors is null or total_floors > 0),
  -- Jalali year for Iranian market
  build_year int check (build_year between 1100 and 1500),
  has_elevator boolean not null default false,
  has_parking boolean not null default false,
  has_storage boolean not null default false,
  has_balcony boolean not null default false,
  orientation text,
  renovation_status text,
  heating text,
  cooling text,
  deed_status text,
  ownership_count int check (ownership_count is null or ownership_count > 0),
  occupancy_status text,
  has_tenant boolean not null default false,
  description text check (description is null or char_length(description) <= 8000),
  latitude double precision check (latitude is null or (latitude >= -90 and latitude <= 90)),
  longitude double precision check (longitude is null or (longitude >= -180 and longitude <= 180)),
  neighborhood text,
  city text not null,
  province text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create trigger properties_set_updated_at
  before update on properties
  for each row execute function public.set_updated_at();

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
  storage_path text not null unique,
  media_type text not null check (media_type in ('image','video','floor_plan','document')),
  mime_type text not null check (mime_type in (
    'image/jpeg','image/png','image/webp',
    'video/mp4',
    'application/pdf'
  )),
  byte_size int not null check (byte_size > 0 and byte_size <= 52428800),
  width int,
  height int,
  sort_order int not null default 0 check (sort_order >= 0),
  is_cover boolean not null default false,
  blurhash text,
  data_source text not null default 'REAL'
    check (data_source in ('REAL','DEV_FIXTURE')),
  created_at timestamptz not null default now()
);

create index idx_media_property on property_media (property_id, sort_order);
-- At most one cover per property
create unique index idx_media_one_cover
  on property_media (property_id)
  where is_cover;

create table property_features (
  property_id uuid not null references properties(id) on delete cascade,
  feature_key text not null,
  feature_value text,
  primary key (property_id, feature_key)
);

-- ---- Listings ----

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
  deposit_rial bigint check (deposit_rial is null or deposit_rial >= 0),
  rent_rial bigint check (rent_rial is null or rent_rial >= 0),
  terms text,
  status listing_status not null default 'DRAFT',
  published_at timestamptz,
  expires_at timestamptz,
  -- Freshness (anti dead listings)
  last_verified_at timestamptz not null default now(),
  last_updated_at timestamptz not null default now(),
  verification_status text not null default 'unverified'
    check (verification_status in ('unverified','pending','verified','rejected')),
  freshness text not null default 'fresh'
    check (freshness in ('fresh','aging','stale')),
  data_source text not null default 'REAL'
    check (data_source in ('REAL','DEV_FIXTURE')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  constraint listings_one_party check (seller_id is not null or agent_id is not null),
  constraint listings_expiry_after_publish check (
    expires_at is null or published_at is null or expires_at > published_at
  )
);

create trigger listings_set_updated_at
  before update on listings
  for each row execute function public.set_updated_at();

create index idx_listings_status_published on listings (status, published_at desc nulls last);
create index idx_listings_agent on listings (agent_id) where deleted_at is null;
create index idx_listings_seller on listings (seller_id) where deleted_at is null;
create index idx_listings_property on listings (property_id) where deleted_at is null;
create index idx_listings_freshness on listings (freshness, last_verified_at);
create index idx_listings_deal_price on listings (deal_type, price_rial)
  where status = 'ACTIVE' and deleted_at is null;

create table listing_status_history (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id) on delete cascade,
  from_status listing_status,
  to_status listing_status not null,
  changed_by uuid references users(id),
  reason text,
  created_at timestamptz not null default now()
);

create index idx_status_history_listing on listing_status_history (listing_id, created_at desc);

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

create index idx_verifications_listing on property_verifications (listing_id, kind);

create table property_price_history (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references listings(id) on delete cascade,
  price_rial bigint not null check (price_rial > 0),
  deposit_rial bigint,
  rent_rial bigint,
  changed_at timestamptz not null default now(),
  changed_by uuid references users(id)
);

create index idx_price_history_listing on property_price_history (listing_id, changed_at desc);

-- Read model: price per sqm joins properties.area_sqm (not a generated column
-- on listings, which cannot see another table).
create view listing_prices as
select
  l.id as listing_id,
  l.property_id,
  l.deal_type,
  l.price_rial,
  case when p.area_sqm > 0
    then l.price_rial / p.area_sqm
    else null
  end as price_per_sqm_rial,
  p.area_sqm,
  l.status,
  l.published_at,
  l.freshness,
  l.data_source
from listings l
join properties p on p.id = l.property_id
where l.deleted_at is null and p.deleted_at is null;
