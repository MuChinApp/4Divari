-- 00094: Phase 5 — agent tooling.
-- assignment, CRM lead on contact, buyer requirements per lead,
-- matching engine (SECURITY DEFINER — matches service write per 0007 note),
-- dashboard stats.

-- ---- Requirements can belong to an agent's lead ----
alter table buyer_requirements
  add column if not exists lead_id uuid references leads(id) on delete set null;

create unique index if not exists idx_requirements_one_per_lead
  on buyer_requirements (lead_id)
  where lead_id is not null;

-- Agent reads requirements of their own leads (leads policy is a plain
-- agent_id check → no policy recursion with matches/requirements).
drop policy if exists requirements_lead_read on buyer_requirements;
create policy requirements_lead_read on buyer_requirements
  for select using (
    exists (
      select 1 from leads ld
      where ld.id = buyer_requirements.lead_id
        and ld.agent_id = public.current_user_id()
    )
  );

-- ---- Assign an agent to a listing (seller or current agent acts) ----
create or replace function public.assign_agent(
  p_listing_id uuid,
  p_agent_phone text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_listing record;
  v_agent uuid;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  select id, seller_id, agent_id, deleted_at
    into v_listing
  from listings
  where id = p_listing_id;

  if v_listing.id is null or v_listing.deleted_at is not null then
    raise exception 'listing not found';
  end if;
  if v_listing.seller_id is distinct from v_uid
     and v_listing.agent_id is distinct from v_uid then
    raise exception 'not a listing party';
  end if;

  if p_agent_phone is null then
    update listings set agent_id = null where id = p_listing_id;
    return jsonb_build_object('agent_id', null);
  end if;

  select u.id into v_agent
  from users u
  where u.phone_e164 = p_agent_phone
    and u.deleted_at is null
    and u.status = 'active'
    and exists (
      select 1 from user_roles ur
      where ur.user_id = u.id and ur.role = 'AGENT'
    )
    and u.id <> v_uid;

  if v_agent is null then
    raise exception 'no active agent with this phone';
  end if;

  update listings set agent_id = v_agent where id = p_listing_id;
  return jsonb_build_object('agent_id', v_agent);
end;
$$;

comment on function public.assign_agent(uuid, text) is
  'SECURITY DEFINER: seller/current agent assigns (phone=null unassigns). Target must hold AGENT role.';

-- ---- listing_contact: also opens a CRM lead for the listing agent ----
drop function if exists public.listing_contact(uuid);

create function public.listing_contact(p_listing_id uuid)
returns table (
  phone_e164 text,
  display_name text,
  party_role text,
  lead_created boolean
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_agent uuid;
  v_exists boolean := false;
  v_created boolean := false;
begin
  -- Resolve contact party + agent (read-only output).
  select u.phone_e164,
         coalesce(nullif(p.display_name, ''),
                  case when l.agent_id is not null then 'مشاور' else 'مالک' end),
         case when l.agent_id is not null then 'agent' else 'seller' end,
         l.agent_id
    into phone_e164, display_name, party_role, v_agent
  from listings l
  join users u on u.id = coalesce(l.agent_id, l.seller_id)
  left join profiles p on p.user_id = u.id
  where l.id = p_listing_id
    and l.deleted_at is null
    and l.status in ('ACTIVE', 'UNDER_OFFER', 'SOLD', 'RENTED')
    and u.deleted_at is null;

  if phone_e164 is null then
    return;
  end if;

  -- CRM side effect: authenticated non-party contact opens one open lead.
  if v_uid is not null
     and v_agent is not null
     and v_uid <> v_agent then
    select exists (
      select 1 from leads ld
      where ld.agent_id = v_agent
        and ld.listing_id = p_listing_id
        and ld.buyer_id = v_uid
        and ld.stage not in ('CLOSED', 'LOST')
    ) into v_exists;

    if not v_exists then
      insert into leads (agent_id, buyer_id, listing_id, source, stage)
      values (v_agent, v_uid, p_listing_id, 'contact', 'NEW');
      v_created := true;
    end if;
  end if;

  lead_created := v_created;
  return next;
end;
$$;

comment on function public.listing_contact(uuid) is
  'SECURITY DEFINER: contact party + idempotent lead creation for the listing agent (authenticated callers).';

-- ---- Agent dashboard stats (single round trip) ----
create or replace function public.agent_dashboard_stats()
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_stats jsonb;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  select jsonb_build_object(
    'files_active', coalesce((select count(*) from listings
      where agent_id = v_uid and status = 'ACTIVE' and deleted_at is null), 0),
    'files_draft', coalesce((select count(*) from listings
      where agent_id = v_uid and status in ('DRAFT','PENDING_VERIFICATION') and deleted_at is null), 0),
    'leads_total', coalesce((select count(*) from leads where agent_id = v_uid), 0),
    'leads_new', coalesce((select count(*) from leads
      where agent_id = v_uid and stage = 'NEW'), 0),
    'leads_open', coalesce((select count(*) from leads
      where agent_id = v_uid and stage not in ('CLOSED','LOST')), 0),
    'visits_pending', coalesce((select count(*) from visits
      where agent_id = v_uid and status = 'REQUESTED'), 0),
    'matches_top', coalesce((select count(*) from matches m
      join listings l on l.id = m.listing_id
      join buyer_requirements br on br.id = m.requirement_id
      where l.agent_id = v_uid and m.score >= 70 and br.is_active), 0)
  ) into v_stats;

  return v_stats;
end;
$$;

comment on function public.agent_dashboard_stats() is
  'SECURITY DEFINER: aggregate counters for the caller agent dashboard.';

-- ---- Agent records a buyer requirement against their lead ----
create or replace function public.agent_upsert_requirement(
  p_lead_id uuid,
  p_deal_type deal_type,
  p_budget_min_rial bigint default null,
  p_budget_max_rial bigint default null,
  p_area_min int default null,
  p_area_max int default null,
  p_bedrooms int[] default null,
  p_cities text[] default null,
  p_features jsonb default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_lead record;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  select id, agent_id, buyer_id into v_lead
  from leads where id = p_lead_id;

  if v_lead.id is null or v_lead.agent_id <> v_uid then
    raise exception 'lead not found or not owned';
  end if;
  if v_lead.buyer_id is null then
    raise exception 'lead has no linked buyer';
  end if;
  if p_budget_min_rial is not null and p_budget_max_rial is not null
     and p_budget_min_rial > p_budget_max_rial then
    raise exception 'budget range invalid';
  end if;
  if p_area_min is not null and p_area_max is not null and p_area_min > p_area_max then
    raise exception 'area range invalid';
  end if;

  update buyer_requirements set
    deal_type = p_deal_type,
    budget_min_rial = p_budget_min_rial,
    budget_max_rial = p_budget_max_rial,
    area_min = p_area_min,
    area_max = p_area_max,
    bedrooms = p_bedrooms,
    cities = p_cities,
    features = p_features,
    is_active = true,
    updated_at = now()
  where lead_id = p_lead_id
  returning id into v_id;

  if v_id is null then
    insert into buyer_requirements (
      buyer_id, lead_id, deal_type,
      budget_min_rial, budget_max_rial, area_min, area_max,
      bedrooms, cities, features, is_active
    ) values (
      v_lead.buyer_id, p_lead_id, p_deal_type,
      p_budget_min_rial, p_budget_max_rial, p_area_min, p_area_max,
      p_bedrooms, p_cities, p_features, true
    )
    returning id into v_id;
  end if;

  return v_id;
end;
$$;

comment on function public.agent_upsert_requirement(uuid, deal_type, bigint, bigint, int, int, int[], text[], jsonb) is
  'SECURITY DEFINER: agent upserts the buyer requirement of their own lead (buyer must be linked).';

-- ---- Matching engine (SECURITY DEFINER — matches write is service-side per 0007) ----
create or replace function public.refresh_lead_matches(p_lead_id uuid)
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_lead record;
  v_req record;
  v_count int := 0;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  select id, agent_id into v_lead
  from leads where id = p_lead_id;
  if v_lead.id is null or v_lead.agent_id <> v_uid then
    raise exception 'lead not found or not owned';
  end if;

  select id into v_req
  from buyer_requirements
  where lead_id = p_lead_id and is_active
  limit 1;
  if v_req.id is null then
    raise exception 'no active requirement for this lead';
  end if;

  -- Recompute from scratch for this requirement (my ACTIVE listings, same deal).
  delete from matches where requirement_id = v_req.id;

  insert into matches (requirement_id, listing_id, score, explanation)
  select
    br.id,
    l.id,
    s.score,
    s.explanation
  from buyer_requirements br
  join listings l
    on l.agent_id = v_uid
   and l.deleted_at is null
   and l.status = 'ACTIVE'
   and l.deal_type = br.deal_type
  join properties pr on pr.id = l.property_id and pr.deleted_at is null
  cross join lateral (
    select
      round(
        (
          case when br.cities is not null and cardinality(br.cities) > 0 then
            case when pr.city = any(br.cities) then 25 else 0 end
          else 0 end
          +
          case when br.budget_min_rial is not null or br.budget_max_rial is not null then
            case
              when (br.budget_min_rial is null or l.price_rial >= br.budget_min_rial)
               and (br.budget_max_rial is null or l.price_rial <= br.budget_max_rial)
                then 25
              when (br.budget_min_rial is null or l.price_rial >= br.budget_min_rial * 0.9)
               and (br.budget_max_rial is null or l.price_rial <= br.budget_max_rial * 1.1)
                then 15
              else 0 end
          else 0 end
          +
          case when br.area_min is not null or br.area_max is not null then
            case
              when (br.area_min is null or pr.area_sqm >= br.area_min)
               and (br.area_max is null or pr.area_sqm <= br.area_max)
                then 20
              when (br.area_min is null or pr.area_sqm >= br.area_min * 0.9)
               and (br.area_max is null or pr.area_sqm <= br.area_max * 1.1)
                then 10
              else 0 end
          else 0 end
          +
          case when br.bedrooms is not null and cardinality(br.bedrooms) > 0 then
            case when pr.bedrooms = any(br.bedrooms) then 15 else 0 end
          else 0 end
          +
          case when br.features is not null and exists (
                 select 1 from jsonb_each_text(br.features) f
                 where f.value = 'true'
                   and f.key in ('has_elevator','has_parking','has_storage','has_balcony')
               ) then
            round(
              15.0 * (
                select count(*)
                from jsonb_each_text(br.features) f
                where f.value = 'true'
                  and case f.key
                    when 'has_elevator' then pr.has_elevator
                    when 'has_parking' then pr.has_parking
                    when 'has_storage' then pr.has_storage
                    when 'has_balcony' then pr.has_balcony
                    else false
                  end
              ) / (
                select count(*)
                from jsonb_each_text(br.features) f
                where f.value = 'true'
                  and f.key in ('has_elevator','has_parking','has_storage','has_balcony')
              )
            )
          else 0 end
        )::numeric
        * 100.0 / nullif(
            (case when br.cities is not null and cardinality(br.cities) > 0 then 25 else 0 end)
          + (case when br.budget_min_rial is not null or br.budget_max_rial is not null then 25 else 0 end)
          + (case when br.area_min is not null or br.area_max is not null then 20 else 0 end)
          + (case when br.bedrooms is not null and cardinality(br.bedrooms) > 0 then 15 else 0 end)
          + (case when br.features is not null and exists (
                   select 1 from jsonb_each_text(br.features) f
                   where f.value = 'true'
                     and f.key in ('has_elevator','has_parking','has_storage','has_balcony')
                 ) then 15 else 0 end),
          0
        ),
        2
      ) as score,
      jsonb_build_object(
        'city', case when br.cities is not null and cardinality(br.cities) > 0
                 then (pr.city = any(br.cities)) else null end,
        'budget', case
          when br.budget_min_rial is null and br.budget_max_rial is null then 'absent'
          when (br.budget_min_rial is null or l.price_rial >= br.budget_min_rial)
           and (br.budget_max_rial is null or l.price_rial <= br.budget_max_rial) then 'exact'
          when (br.budget_min_rial is null or l.price_rial >= br.budget_min_rial * 0.9)
           and (br.budget_max_rial is null or l.price_rial <= br.budget_max_rial * 1.1) then 'tolerant'
          else 'miss' end,
        'area', case
          when br.area_min is null and br.area_max is null then 'absent'
          when (br.area_min is null or pr.area_sqm >= br.area_min)
           and (br.area_max is null or pr.area_sqm <= br.area_max) then 'exact'
          when (br.area_min is null or pr.area_sqm >= br.area_min * 0.9)
           and (br.area_max is null or pr.area_sqm <= br.area_max * 1.1) then 'tolerant'
          else 'miss' end,
        'bedrooms', case
          when br.bedrooms is null or cardinality(br.bedrooms) = 0 then null
          else (pr.bedrooms = any(br.bedrooms)) end,
        'price_rial', l.price_rial,
        'city_name', pr.city
      ) as explanation
  ) s
  where br.id = v_req.id
    and s.score is not null
    and s.score > 0;

  get diagnostics v_count = row_count;
  return v_count;
end;
$$;

comment on function public.refresh_lead_matches(uuid) is
  'SECURITY DEFINER: recompute 0-100 match scores (normalized over present criteria) for one lead requirement x caller ACTIVE listings.';

-- ---- Grants (conditional roles; same pattern as 00092/00093) ----
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'anon') then
    grant execute on function public.listing_contact(uuid) to anon;
    grant execute on function public.assign_agent(uuid, text) to anon;
    grant execute on function public.agent_dashboard_stats() to anon;
    grant execute on function public.refresh_lead_matches(uuid) to anon;
    grant execute on function public.agent_upsert_requirement(uuid, deal_type, bigint, bigint, int, int, int[], text[], jsonb) to anon;
  end if;
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function public.listing_contact(uuid) to authenticated;
    grant execute on function public.assign_agent(uuid, text) to authenticated;
    grant execute on function public.agent_dashboard_stats() to authenticated;
    grant execute on function public.refresh_lead_matches(uuid) to authenticated;
    grant execute on function public.agent_upsert_requirement(uuid, deal_type, bigint, bigint, int, int, int[], text[], jsonb) to authenticated;
  end if;
  if exists (select 1 from pg_roles where rolname = 'app_user') then
    grant execute on function public.listing_contact(uuid) to app_user;
    grant execute on function public.assign_agent(uuid, text) to app_user;
    grant execute on function public.agent_dashboard_stats() to app_user;
    grant execute on function public.refresh_lead_matches(uuid) to app_user;
    grant execute on function public.agent_upsert_requirement(uuid, deal_type, bigint, bigint, int, int, int[], text[], jsonb) to app_user;
  end if;
end;
$$;
