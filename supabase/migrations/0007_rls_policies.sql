-- 0007: Row Level Security — enable on EVERY table, then policies.
-- Principle: no private data reachable by ID alone.

alter table users enable row level security;
alter table profiles enable row level security;
alter table roles enable row level security;
alter table user_roles enable row level security;
alter table permissions enable row level security;
alter table role_permissions enable row level security;
alter table agencies enable row level security;
alter table agency_members enable row level security;
alter table properties enable row level security;
alter table property_addresses enable row level security;
alter table property_media enable row level security;
alter table property_features enable row level security;
alter table listings enable row level security;
alter table listing_status_history enable row level security;
alter table property_verifications enable row level security;
alter table property_price_history enable row level security;
alter table favorites enable row level security;
alter table saved_searches enable row level security;
alter table search_events enable row level security;
alter table buyer_requirements enable row level security;
alter table matches enable row level security;
alter table leads enable row level security;
alter table conversations enable row level security;
alter table conversation_participants enable row level security;
alter table messages enable row level security;
alter table visits enable row level security;
alter table offers enable row level security;
alter table offer_events enable row level security;
alter table notifications enable row level security;
alter table reports enable row level security;
alter table reviews enable row level security;
alter table audit_logs enable row level security;

-- Helper predicates (security definer avoids recursive RLS on user_roles)
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from user_roles
    where user_id = public.current_user_id() and role = 'ADMIN'
  );
$$;

create or replace function public.has_role(r role_code)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from user_roles
    where user_id = public.current_user_id() and role = r
  );
$$;

create or replace function public.is_agency_member(agency uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from agency_members
    where agency_id = agency and user_id = public.current_user_id()
  );
$$;

create or replace function public.is_listing_party(l uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from listings x
    where x.id = l
      and (
        x.seller_id = public.current_user_id()
        or x.agent_id = public.current_user_id()
        or (x.agency_id is not null and public.is_agency_member(x.agency_id))
      )
  );
$$;

-- ========== users / profiles / roles ==========

drop policy if exists users_self_read on users;
create policy users_self_read on users
  for select using (
    id = public.current_user_id() or public.is_admin()
  );

drop policy if exists users_self_update on users;
create policy users_self_update on users
  for update using (id = public.current_user_id())
  with check (id = public.current_user_id());

-- Phone uniqueness + verified_at only set once (app layer + this check)
-- Admin may suspend
drop policy if exists users_admin_all on users;
create policy users_admin_all on users
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists profiles_public_read on profiles;
create policy profiles_public_read on profiles
  for select using (true);

drop policy if exists profiles_self_write on profiles;
create policy profiles_self_write on profiles
  for update using (user_id = public.current_user_id())
  with check (user_id = public.current_user_id());

drop policy if exists profiles_self_insert on profiles;
create policy profiles_self_insert on profiles
  for insert with check (user_id = public.current_user_id());

-- roles / permissions: readable by all authenticated (and anon for labels)
drop policy if exists roles_read on roles;
create policy roles_read on roles for select using (true);

drop policy if exists permissions_read on permissions;
create policy permissions_read on permissions for select using (true);

drop policy if exists role_permissions_read on role_permissions;
create policy role_permissions_read on role_permissions for select using (true);

drop policy if exists user_roles_self_read on user_roles;
create policy user_roles_self_read on user_roles
  for select using (
    user_id = public.current_user_id() or public.is_admin()
  );

-- Only admin can grant roles (prevents privilege escalation)
drop policy if exists user_roles_admin_write on user_roles;
create policy user_roles_admin_write on user_roles
  for all using (public.is_admin()) with check (public.is_admin());

-- ========== agencies ==========

drop policy if exists agencies_public_read on agencies;
create policy agencies_public_read on agencies
  for select using (deleted_at is null);

drop policy if exists agencies_owner_write on agencies;
create policy agencies_owner_write on agencies
  for update using (owner_id = public.current_user_id())
  with check (owner_id = public.current_user_id());

drop policy if exists agencies_member_read on agency_members;
create policy agencies_member_read on agency_members
  for select using (
    user_id = public.current_user_id()
    or public.is_agency_member(agency_id)
    or public.is_admin()
  );

drop policy if exists agencies_owner_manage_members on agency_members;
create policy agencies_owner_manage_members on agency_members
  for all using (
    exists (
      select 1 from agencies a
      where a.id = agency_id and a.owner_id = public.current_user_id()
    ) or public.is_admin()
  )
  with check (
    exists (
      select 1 from agencies a
      where a.id = agency_id and a.owner_id = public.current_user_id()
    ) or public.is_admin()
  );

-- ========== properties / listings (public read for ACTIVE) ==========

drop policy if exists properties_public_read on properties;
create policy properties_public_read on properties
  for select using (
    deleted_at is null
    and exists (
      select 1 from listings l
      where l.property_id = properties.id
        and l.status in ('ACTIVE','UNDER_OFFER','SOLD','RENTED')
        and l.deleted_at is null
    )
    or public.is_admin()
  );

drop policy if exists properties_party_write on properties;
create policy properties_party_write on properties
  for all using (public.is_admin())
  with check (public.is_admin());
-- Note: property inserts go through listing draft RPCs / service role in Phase 4.
-- Direct client writes restricted to admin until seller wizard lands with RPCs.

drop policy if exists property_addresses_party_read on property_addresses;
create policy property_addresses_party_read on property_addresses
  for select using (
    public.is_admin()
    or exists (
      select 1 from listings l
      where l.property_id = property_addresses.property_id
        and (
          l.status = 'ACTIVE'
          or l.seller_id = public.current_user_id()
          or l.agent_id = public.current_user_id()
        )
    )
  );

drop policy if exists property_media_public_read on property_media;
create policy property_media_public_read on property_media
  for select using (true);

drop policy if exists property_features_public_read on property_features;
create policy property_features_public_read on property_features
  for select using (true);

drop policy if exists listings_public_active_read on listings;
create policy listings_public_active_read on listings
  for select using (
    (
      deleted_at is null
      and (
        status in ('ACTIVE','UNDER_OFFER','SOLD','RENTED')
        or seller_id = public.current_user_id()
        or agent_id = public.current_user_id()
        or (agency_id is not null and public.is_agency_member(agency_id))
      )
    ) or public.is_admin()
  );

drop policy if exists listings_party_insert on listings;
create policy listings_party_insert on listings
  for insert with check (
    (
      seller_id = public.current_user_id()
      or agent_id = public.current_user_id()
    )
    and status in ('DRAFT','PENDING_VERIFICATION')
    or public.is_admin()
  );

drop policy if exists listings_party_update on listings;
create policy listings_party_update on listings
  for update using (public.is_listing_party(id) or public.is_admin())
  with check (public.is_listing_party(id) or public.is_admin());

drop policy if exists listings_party_delete on listings;
create policy listings_party_delete on listings
  for delete using (
    (seller_id = public.current_user_id() or agent_id = public.current_user_id())
    and status in ('DRAFT','REJECTED','EXPIRED')
    or public.is_admin()
  );

drop policy if exists status_history_party_read on listing_status_history;
create policy status_history_party_read on listing_status_history
  for select using (public.is_listing_party(listing_id) or public.is_admin());

drop policy if exists verifications_party_read on property_verifications;
create policy verifications_party_read on property_verifications
  for select using (public.is_listing_party(listing_id) or public.is_admin());

-- Verification decisions: admin only
drop policy if exists verifications_admin_write on property_verifications;
create policy verifications_admin_write on property_verifications
  for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists price_history_public_read on property_price_history;
create policy price_history_public_read on property_price_history
  for select using (true);

-- ========== favorites / saved searches ==========

drop policy if exists favorites_self_all on favorites;
create policy favorites_self_all on favorites
  for all using (user_id = public.current_user_id())
  with check (user_id = public.current_user_id());

drop policy if exists saved_searches_self_all on saved_searches;
create policy saved_searches_self_all on saved_searches
  for all using (user_id = public.current_user_id())
  with check (user_id = public.current_user_id());

drop policy if exists search_events_self_insert on search_events;
create policy search_events_self_insert on search_events
  for insert with check (
    user_id is null or user_id = public.current_user_id()
  );

drop policy if exists search_events_self_read on search_events;
create policy search_events_self_read on search_events
  for select using (user_id = public.current_user_id() or public.is_admin());

-- ========== requirements / matches / leads ==========

drop policy if exists requirements_self_all on buyer_requirements;
create policy requirements_self_all on buyer_requirements
  for all using (buyer_id = public.current_user_id())
  with check (buyer_id = public.current_user_id());

drop policy if exists matches_requirement_owner_read on matches;
create policy matches_requirement_owner_read on matches
  for select using (
    exists (
      select 1 from buyer_requirements br
      where br.id = requirement_id and br.buyer_id = public.current_user_id()
    )
    or public.is_listing_party(listing_id)
    or public.is_admin()
  );

drop policy if exists matches_service_write on matches;
create policy matches_service_write on matches
  for insert with check (public.is_admin());
-- Matching engine writes via service role / SECURITY DEFINER RPC (Phase 5).

drop policy if exists leads_agent_read on leads;
create policy leads_agent_read on leads
  for select using (agent_id = public.current_user_id() or public.is_admin());

drop policy if exists leads_agent_write on leads;
create policy leads_agent_write on leads
  for insert with check (agent_id = public.current_user_id());

drop policy if exists leads_agent_update on leads;
create policy leads_agent_update on leads
  for update using (agent_id = public.current_user_id())
  with check (agent_id = public.current_user_id());

-- ========== conversations / messages ==========

drop policy if exists conversations_participant_read on conversations;
create policy conversations_participant_read on conversations
  for select using (
    exists (
      select 1 from conversation_participants cp
      where cp.conversation_id = conversations.id
        and cp.user_id = public.current_user_id()
    ) or public.is_admin()
  );

drop policy if exists participants_self_read on conversation_participants;
create policy participants_self_read on conversation_participants
  for select using (
    user_id = public.current_user_id()
    or exists (
      select 1 from conversation_participants cp
      where cp.conversation_id = conversation_participants.conversation_id
        and cp.user_id = public.current_user_id()
    )
    or public.is_admin()
  );

drop policy if exists participants_self_join on conversation_participants;
create policy participants_self_join on conversation_participants
  for insert with check (user_id = public.current_user_id());

drop policy if exists messages_participant_read on messages;
create policy messages_participant_read on messages
  for select using (
    exists (
      select 1 from conversation_participants cp
      where cp.conversation_id = messages.conversation_id
        and cp.user_id = public.current_user_id()
    ) or public.is_admin()
  );

drop policy if exists messages_participant_insert on messages;
create policy messages_participant_insert on messages
  for insert with check (
    sender_id = public.current_user_id()
    and exists (
      select 1 from conversation_participants cp
      where cp.conversation_id = messages.conversation_id
        and cp.user_id = public.current_user_id()
    )
  );

-- ========== visits / offers ==========

drop policy if exists visits_party_read on visits;
create policy visits_party_read on visits
  for select using (
    buyer_id = public.current_user_id()
    or agent_id = public.current_user_id()
    or public.is_listing_party(listing_id)
    or public.is_admin()
  );

drop policy if exists visits_buyer_insert on visits;
create policy visits_buyer_insert on visits
  for insert with check (
    buyer_id = public.current_user_id()
    and status = 'REQUESTED'
  );

drop policy if exists visits_party_update on visits;
create policy visits_party_update on visits
  for update using (
    buyer_id = public.current_user_id()
    or agent_id = public.current_user_id()
    or public.is_listing_party(listing_id)
    or public.is_admin()
  )
  with check (true);

drop policy if exists offers_party_read on offers;
create policy offers_party_read on offers
  for select using (
    buyer_id = public.current_user_id()
    or public.is_listing_party(listing_id)
    or public.is_admin()
  );

drop policy if exists offers_buyer_insert on offers;
create policy offers_buyer_insert on offers
  for insert with check (buyer_id = public.current_user_id());

drop policy if exists offers_party_update on offers;
create policy offers_party_update on offers
  for update using (
    buyer_id = public.current_user_id()
    or public.is_listing_party(listing_id)
    or public.is_admin()
  )
  with check (true);

drop policy if exists offer_events_party_read on offer_events;
create policy offer_events_party_read on offer_events
  for select using (
    exists (
      select 1 from offers o
      where o.id = offer_id
        and (
          o.buyer_id = public.current_user_id()
          or public.is_listing_party(o.listing_id)
        )
    ) or public.is_admin()
  );

-- offer_events insert happens in trigger (security definer) + parties
drop policy if exists offer_events_party_insert on offer_events;
create policy offer_events_party_insert on offer_events
  for insert with check (
    actor_id = public.current_user_id()
    or public.is_admin()
  );

-- ========== notifications / reports / reviews / audit ==========

drop policy if exists notifications_self_all on notifications;
create policy notifications_self_all on notifications
  for all using (user_id = public.current_user_id())
  with check (user_id = public.current_user_id());

drop policy if exists reports_anyone_insert on reports;
create policy reports_anyone_insert on reports
  for insert with check (
    reporter_id is null or reporter_id = public.current_user_id()
  );

drop policy if exists reports_reporter_admin_read on reports;
create policy reports_reporter_admin_read on reports
  for select using (
    reporter_id = public.current_user_id() or public.is_admin()
  );

drop policy if exists reports_admin_update on reports;
create policy reports_admin_update on reports
  for update using (public.is_admin()) with check (public.is_admin());

drop policy if exists reviews_public_read on reviews;
create policy reviews_public_read on reviews
  for select using (true);

drop policy if exists reviews_author_insert on reviews;
create policy reviews_author_insert on reviews
  for insert with check (author_id = public.current_user_id());

-- audit_logs: admin read; inserts from SECURITY DEFINER triggers / service role
drop policy if exists audit_admin_read on audit_logs;
create policy audit_admin_read on audit_logs
  for select using (public.is_admin());

drop policy if exists audit_insert_service on audit_logs;
create policy audit_insert_service on audit_logs
  for insert with check (true);
