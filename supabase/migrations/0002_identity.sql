-- 0002: identity — users, profiles, multi-role, agencies

create type user_status as enum ('pending', 'active', 'suspended', 'deleted');

create table users (
  id uuid primary key default gen_random_uuid(),
  -- Phone is the primary credential (OTP). Never store OTP codes here.
  phone_e164 text not null unique
    check (phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
  phone_verified_at timestamptz,
  status user_status not null default 'pending',
  last_login_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create trigger users_set_updated_at
  before update on users
  for each row execute function public.set_updated_at();

create table profiles (
  user_id uuid primary key references users(id) on delete cascade,
  display_name text check (display_name is null or char_length(display_name) between 1 and 120),
  bio text check (bio is null or char_length(bio) <= 2000),
  avatar_path text,
  locale text not null default 'fa_IR',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger profiles_set_updated_at
  before update on profiles
  for each row execute function public.set_updated_at();

create type role_code as enum (
  'GUEST','BUYER','TENANT','SELLER','LANDLORD',
  'AGENT','AGENCY_OWNER','AGENCY_STAFF','ADMIN','VERIFIED_PROFESSIONAL'
);

create table roles (
  code role_code primary key,
  description text
);

insert into roles (code, description) values
  ('GUEST', 'Unauthenticated browse'),
  ('BUYER', 'Registered buyer/seeker'),
  ('TENANT', 'Rental seeker'),
  ('SELLER', 'Property seller'),
  ('LANDLORD', 'Property owner/landlord'),
  ('AGENT', 'Licensed real-estate consultant'),
  ('AGENCY_OWNER', 'Agency administrator'),
  ('AGENCY_STAFF', 'Agency employee'),
  ('ADMIN', 'Platform administrator'),
  ('VERIFIED_PROFESSIONAL', 'Identity-verified professional');

-- Multi-role: NEVER a single user.role column.
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

insert into permissions (code, description) values
  ('listing.create', 'Create a listing'),
  ('listing.moderate', 'Moderate listings'),
  ('lead.view', 'View assigned leads'),
  ('agency.manage', 'Manage agency members'),
  ('admin.users', 'Administrate users');

create table role_permissions (
  role role_code not null references roles(code) on delete cascade,
  permission text not null references permissions(code) on delete cascade,
  primary key (role, permission)
);

insert into role_permissions (role, permission) values
  ('SELLER', 'listing.create'),
  ('LANDLORD', 'listing.create'),
  ('AGENT', 'listing.create'),
  ('AGENT', 'lead.view'),
  ('AGENCY_OWNER', 'listing.create'),
  ('AGENCY_OWNER', 'lead.view'),
  ('AGENCY_OWNER', 'agency.manage'),
  ('AGENCY_STAFF', 'lead.view'),
  ('ADMIN', 'listing.moderate'),
  ('ADMIN', 'admin.users'),
  ('ADMIN', 'agency.manage');

create table agencies (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 2 and 200),
  owner_id uuid not null references users(id),
  phone_e164 text check (phone_e164 is null or phone_e164 ~ '^\+[1-9][0-9]{6,14}$'),
  city text,
  verified_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create trigger agencies_set_updated_at
  before update on agencies
  for each row execute function public.set_updated_at();

create table agency_members (
  agency_id uuid not null references agencies(id) on delete cascade,
  user_id uuid not null references users(id) on delete cascade,
  member_role text not null check (member_role in ('owner','manager','staff')),
  joined_at timestamptz not null default now(),
  primary key (agency_id, user_id)
);

create index idx_user_roles_user on user_roles (user_id);
create index idx_users_phone on users (phone_e164) where deleted_at is null;
create index idx_agency_members_user on agency_members (user_id);
