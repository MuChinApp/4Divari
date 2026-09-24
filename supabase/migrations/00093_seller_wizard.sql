-- 00093: Phase 4 — seller wizard RPCs + media party write + status transitions.

-- ---- Atomic draft creation (property ≠ listing, one transaction) ----
create or replace function public.seller_create_draft(
  p_property jsonb,
  p_listing jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := public.current_user_id();
  v_property_id uuid;
  v_listing_id uuid;
  v_deal deal_type;
  v_price bigint;
begin
  if v_user is null then
    raise exception 'authentication required';
  end if;

  if p_property is null or p_listing is null then
    raise exception 'property and listing payloads are required';
  end if;

  insert into properties (
    property_type, area_sqm, built_area_sqm, land_sqm, bedrooms,
    floor, total_floors, build_year,
    has_elevator, has_parking, has_storage, has_balcony,
    orientation, renovation_status, heating, cooling, deed_status,
    has_tenant, description, latitude, longitude,
    neighborhood, city, province
  )
  values (
    (p_property->>'property_type')::property_type,
    (p_property->>'area_sqm')::int,
    nullif(p_property->>'built_area_sqm', '')::int,
    nullif(p_property->>'land_sqm', '')::int,
    coalesce(nullif(p_property->>'bedrooms', '')::int, 0),
    nullif(p_property->>'floor', '')::int,
    nullif(p_property->>'total_floors', '')::int,
    nullif(p_property->>'build_year', '')::int,
    coalesce((p_property->>'has_elevator')::boolean, false),
    coalesce((p_property->>'has_parking')::boolean, false),
    coalesce((p_property->>'has_storage')::boolean, false),
    coalesce((p_property->>'has_balcony')::boolean, false),
    nullif(p_property->>'orientation', ''),
    nullif(p_property->>'renovation_status', ''),
    nullif(p_property->>'heating', ''),
    nullif(p_property->>'cooling', ''),
    nullif(p_property->>'deed_status', ''),
    coalesce((p_property->>'has_tenant')::boolean, false),
    nullif(p_property->>'description', ''),
    nullif(p_property->>'latitude', '')::double precision,
    nullif(p_property->>'longitude', '')::double precision,
    nullif(p_property->>'neighborhood', ''),
    p_property->>'city',
    p_property->>'province'
  )
  returning id into v_property_id;

  v_deal := (p_listing->>'deal_type')::deal_type;
  v_price := (p_listing->>'price_rial')::bigint;

  insert into listings (
    property_id, seller_id, deal_type,
    price_rial, deposit_rial, rent_rial, terms,
    status
  )
  values (
    v_property_id, v_user, v_deal,
    v_price,
    nullif(p_listing->>'deposit_rial', '')::bigint,
    nullif(p_listing->>'rent_rial', '')::bigint,
    nullif(p_listing->>'terms', ''),
    'DRAFT'
  )
  returning id into v_listing_id;

  insert into listing_status_history (listing_id, from_status, to_status, changed_by, reason)
  values (v_listing_id, null, 'DRAFT', v_user, 'seller_create_draft');

  return jsonb_build_object(
    'listing_id', v_listing_id,
    'property_id', v_property_id
  );
end;
$$;

comment on function public.seller_create_draft(jsonb, jsonb) is
  'SECURITY DEFINER: authenticated seller creates property + DRAFT listing atomically.';

-- ---- Controlled status transitions (publish / pause / resume / sold) ----
create or replace function public.seller_set_status(
  p_listing_id uuid,
  p_to listing_status
)
returns listing_status
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user uuid := public.current_user_id();
  v_from listing_status;
  v_ok boolean := false;
begin
  if v_user is null then
    raise exception 'authentication required';
  end if;

  select status into v_from
  from listings
  where id = p_listing_id
    and deleted_at is null
    and (seller_id = v_user or agent_id = v_user);

  if v_from is null then
    raise exception 'listing not found or not owned';
  end if;

  if v_from = p_to then
    return v_from;
  end if;

  v_ok := case v_from
    when 'DRAFT' then p_to in ('PENDING_VERIFICATION', 'ACTIVE')
    when 'PENDING_VERIFICATION' then p_to in ('ACTIVE', 'REJECTED', 'DRAFT')
    when 'ACTIVE' then p_to in ('PAUSED', 'UNDER_OFFER', 'SOLD', 'RENTED', 'EXPIRED')
    when 'PAUSED' then p_to in ('ACTIVE', 'SOLD', 'RENTED', 'EXPIRED')
    when 'UNDER_OFFER' then p_to in ('ACTIVE', 'SOLD', 'RENTED')
    else false
  end;

  if not v_ok then
    raise exception 'illegal transition % -> %', v_from, p_to;
  end if;

  update listings set status = p_to where id = p_listing_id;
  return p_to;
end;
$$;

comment on function public.seller_set_status(uuid, listing_status) is
  'SECURITY DEFINER: party-only listing status transitions with explicit map.';

-- ---- Party media upload metadata (storage bytes go through Storage API) ----
drop policy if exists property_media_party_insert on property_media;
create policy property_media_party_insert on property_media
  for insert with check (
    exists (
      select 1 from listings l
      where l.property_id = property_media.property_id
        and (l.seller_id = public.current_user_id()
             or l.agent_id = public.current_user_id())
        and l.deleted_at is null
    )
    or public.is_admin()
  );

drop policy if exists property_media_party_delete on property_media;
create policy property_media_party_delete on property_media
  for delete using (
    exists (
      select 1 from listings l
      where l.property_id = property_media.property_id
        and (l.seller_id = public.current_user_id()
             or l.agent_id = public.current_user_id())
        and l.deleted_at is null
    )
    or public.is_admin()
  );

-- ---- Grants (conditional roles; same pattern as 00092) ----
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'anon') then
    grant execute on function public.seller_create_draft(jsonb, jsonb) to anon;
    grant execute on function public.seller_set_status(uuid, listing_status) to anon;
  end if;
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function public.seller_create_draft(jsonb, jsonb) to authenticated;
    grant execute on function public.seller_set_status(uuid, listing_status) to authenticated;
  end if;
  if exists (select 1 from pg_roles where rolname = 'app_user') then
    grant execute on function public.seller_create_draft(jsonb, jsonb) to app_user;
    grant execute on function public.seller_set_status(uuid, listing_status) to app_user;
  end if;
end;
$$;

-- ---- Storage write policy for seller uploads (platform-only; CI no-ops) ----
do $$
begin
  if exists (select 1 from information_schema.tables where table_schema = 'storage') then
    begin
      execute $policy$
        create policy "property-media authenticated write"
          on storage.objects for insert to authenticated
          with check (bucket_id = 'property-media')
      $policy$;
    exception when duplicate_object then
      null;
    end;
    begin
      execute $policy$
        create policy "property-media owner delete"
          on storage.objects for delete to authenticated
          using (bucket_id = 'property-media')
      $policy$;
    exception when duplicate_object then
      null;
    end;
  end if;
exception when others then
  null;
end;
$$;
