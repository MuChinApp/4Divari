-- RLS verification as non-owner role app_user (FORCE RLS is on).
-- Fixtures created as superuser (bypasses RLS); assertions run as app_user.

begin;

-- ---- Superuser fixtures ----
insert into users (id, phone_e164, phone_verified_at, status)
values
  ('10000000-0000-0000-0000-00000000000a', '+989311111111', now(), 'active'),
  ('20000000-0000-0000-0000-00000000000b', '+989311111112', now(), 'active')
on conflict (phone_e164) do nothing;

insert into properties (id, property_type, area_sqm, city, province)
values ('30000000-0000-0000-0000-00000000000c', 'HOUSE', 120, 'Esfahan', 'Esfahan')
on conflict (id) do nothing;

insert into listings (id, property_id, seller_id, deal_type, price_rial, status)
values ('40000000-0000-0000-0000-00000000000d', '30000000-0000-0000-0000-00000000000c',
        '10000000-0000-0000-0000-00000000000a', 'SALE', 5000000000, 'DRAFT')
on conflict (id) do nothing;

-- ---- Drop to non-privileged role (RLS applies) ----
set role app_user;

-- Act as seller A
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-00000000000a', false);

do $$
declare n int;
begin
  select count(*) into n from listings where id = '40000000-0000-0000-0000-00000000000d';
  if n <> 1 then
    raise exception 'RLS: seller cannot see own draft (got %)', n;
  end if;

  select count(*) into n from users where id = '10000000-0000-0000-0000-00000000000a';
  if n <> 1 then
    raise exception 'RLS: seller cannot read own user row (got %)', n;
  end if;
end;
$$;

-- Seller activates own draft
update listings set status = 'ACTIVE'
where id = '40000000-0000-0000-0000-00000000000d';

do $$
declare n int;
begin
  select count(*) into n from listing_status_history
  where listing_id = '40000000-0000-0000-0000-00000000000d' and to_status = 'ACTIVE';
  if n < 1 then
    raise exception 'status_history not written on seller transition (got %)', n;
  end if;
end;
$$;

-- Switch to stranger B
select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-00000000000b', false);

do $$
declare n int;
begin
  select count(*) into n from listings where id = '40000000-0000-0000-0000-00000000000d';
  if n <> 1 then
    raise exception 'RLS: active listing not public (got %)', n;
  end if;

  select count(*) into n from users where id = '10000000-0000-0000-0000-00000000000a';
  if n <> 0 then
    raise exception 'RLS leak: user B can read user A row';
  end if;

  select count(*) into n from users where phone_e164 = '+989311111111';
  if n <> 0 then
    raise exception 'RLS leak: phone numbers readable by strangers';
  end if;
end;
$$;

-- B must not self-grant ADMIN
do $$
begin
  begin
    insert into user_roles (user_id, role)
    values ('20000000-0000-0000-0000-00000000000b', 'ADMIN');
  exception when others then
    null;
  end;
  if exists (
    select 1 from user_roles
    where user_id = '20000000-0000-0000-0000-00000000000b' and role = 'ADMIN'
  ) then
    raise exception 'Role escalation: user granted ADMIN to self';
  end if;
end;
$$;

-- B must not write favorites as A
do $$
begin
  begin
    insert into favorites (user_id, listing_id)
    values ('10000000-0000-0000-0000-00000000000a',
            '40000000-0000-0000-0000-00000000000d');
  exception when others then
    null;
  end;
  if exists (
    select 1 from favorites
    where user_id = '10000000-0000-0000-0000-00000000000a'
  ) then
    raise exception 'RLS: B wrote favorites for A';
  end if;
end;
$$;

-- B cannot update A listing
do $$
declare n int;
begin
  update listings set price_rial = 1
  where id = '40000000-0000-0000-0000-00000000000d';

  get diagnostics n = row_count;
  if n <> 0 then
    raise exception 'RLS: stranger updated listing (rows=%)', n;
  end if;
end;
$$;

-- ---- Seller wizard RPC smoke (as seller A) ----
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-00000000000a', false);

do $$
declare
  v_created jsonb;
  v_listing uuid;
  v_status listing_status;
  v_count int;
  v_ok boolean;
begin
  -- create draft: property + listing atomically
  v_created := public.seller_create_draft(
    jsonb_build_object(
      'property_type', 'APARTMENT',
      'area_sqm', 95,
      'bedrooms', 2,
      'city', 'Tehran',
      'province', 'Tehran',
      'neighborhood', 'Yangi',
      'description', 'smoke draft'
    ),
    jsonb_build_object(
      'deal_type', 'SALE',
      'price_rial', 4200000000
    )
  );
  if v_created is null or v_created->>'listing_id' is null then
    raise exception 'seller_create_draft returned null';
  end if;
  v_listing := (v_created->>'listing_id')::uuid;
  if v_created->>'property_id' is null then
    raise exception 'seller_create_draft missing property_id';
  end if;

  select status into v_status from listings where id = v_listing;
  if v_status is distinct from 'DRAFT'::listing_status then
    raise exception 'seller_create_draft status expected DRAFT got %', v_status;
  end if;

  select count(*) into v_count from listing_status_history
  where listing_id = v_listing and to_status = 'DRAFT';
  if v_count < 1 then
    raise exception 'draft history missing';
  end if;

  -- publish
  v_status := public.seller_set_status(v_listing, 'ACTIVE');
  if v_status <> 'ACTIVE' then
    raise exception 'publish failed: %', v_status;
  end if;

  select published_at is not null into v_ok from listings where id = v_listing;
  if v_ok is distinct from true then
    raise exception 'published_at not set on ACTIVE';
  end if;

  -- illegal: ACTIVE -> DRAFT
  begin
    perform public.seller_set_status(v_listing, 'DRAFT');
    raise exception 'guard bypassed: ACTIVE to DRAFT was allowed';
  exception
    when raise_exception then
      if sqlerrm like 'illegal transition%' then
        null; -- expected guard error
      else
        raise;
      end if;
  end;

  -- pause then resume then sold
  v_status := public.seller_set_status(v_listing, 'PAUSED');
  if v_status <> 'PAUSED' then
    raise exception 'pause failed';
  end if;
  v_status := public.seller_set_status(v_listing, 'ACTIVE');
  if v_status <> 'ACTIVE' then
    raise exception 'resume failed';
  end if;
  v_status := public.seller_set_status(v_listing, 'SOLD');
  if v_status <> 'SOLD' then
    raise exception 'mark sold failed';
  end if;

  -- media metadata insert on own property
  insert into property_media (property_id, storage_path, media_type, mime_type, byte_size, sort_order, is_cover)
  select property_id, 'smoke/' || id || '/cover.jpg', 'image', 'image/jpeg', 12345, 0, true
  from listings where id = v_listing;
end;
$$;

-- ---- Stranger B must not transition or see A's draft ----
select set_config('request.jwt.claim.sub', '20000000-0000-0000-0000-00000000000b', false);

do $$
declare
  v_listing uuid;
begin
  select id into v_listing from listings
  where seller_id = '10000000-0000-0000-0000-00000000000a'
    and status = 'SOLD'
  limit 1;

  begin
    perform public.seller_set_status(v_listing, 'ACTIVE');
    raise exception 'stranger transitioned A listing';
  exception
    when raise_exception then
      if sqlerrm like 'listing not found%' then
        null; -- expected ownership guard
      elsif sqlerrm = 'stranger transitioned A listing' then
        raise;
      else
        raise;
      end if;
  end;

  -- stranger cannot insert media on A's fixture property
  begin
    insert into property_media (property_id, storage_path, media_type, mime_type, byte_size)
    values ('30000000-0000-0000-0000-00000000000c', 'evil/' || gen_random_uuid() || '.jpg',
            'image', 'image/jpeg', 100);
    raise exception 'stranger inserted media on A property';
  exception
    when others then
      if sqlerrm = 'stranger inserted media on A property' then
        raise;
      end if;
  end;
end;
$$;

reset role;
rollback;

select 'RLS_SMOKE_OK' as result;
