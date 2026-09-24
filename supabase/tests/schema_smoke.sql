-- Smoke tests for 4Divari schema — run with psql -v ON_ERROR_STOP=1 -f ...
-- Fails the transaction on any assertion failure.

begin;

-- 1) All core tables exist
do $$
declare
  t text;
  missing text[] := array[]::text[];
begin
  foreach t in array array[
    'users','profiles','roles','user_roles','permissions','role_permissions',
    'agencies','agency_members','properties','property_addresses','property_media',
    'property_features','listings','listing_status_history','property_verifications',
    'property_price_history','favorites','saved_searches','search_events',
    'buyer_requirements','matches','leads','conversations','conversation_participants',
    'messages','visits','offers','offer_events','notifications','reports','reviews',
    'audit_logs'
  ]
  loop
    if not exists (
      select 1 from information_schema.tables
      where table_schema = 'public' and table_name = t
    ) then
      missing := missing || t;
    end if;
  end loop;

  if array_length(missing, 1) > 0 then
    raise exception 'Missing tables: %', array_to_string(missing, ', ');
  end if;
end;
$$;

-- 2) RLS enabled on every table above
do $$
declare
  r record;
  unprotected text[] := array[]::text[];
begin
  for r in
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relkind = 'r'
      and c.relname in (
        'users','profiles','roles','user_roles','permissions','role_permissions',
        'agencies','agency_members','properties','property_addresses','property_media',
        'property_features','listings','listing_status_history','property_verifications',
        'property_price_history','favorites','saved_searches','search_events',
        'buyer_requirements','matches','leads','conversations','conversation_participants',
        'messages','visits','offers','offer_events','notifications','reports','reviews',
        'audit_logs'
      )
  loop
    if not exists (
      select 1 from pg_class c2
      where c2.oid = (select oid from pg_class where relname = r.relname and relkind = 'r')
        and c2.relrowsecurity
    ) then
      unprotected := unprotected || r.relname;
    end if;
  end loop;

  if array_length(unprotected, 1) > 0 then
    raise exception 'RLS not enabled on: %', array_to_string(unprotected, ', ');
  end if;
end;
$$;

-- 3) Multi-role model: one user, two roles
insert into users (id, phone_e164, phone_verified_at, status)
values ('dddddddd-0000-0000-0000-00000000d001', '+989120000001', now(), 'active')
on conflict (phone_e164) do nothing;

insert into user_roles (user_id, role) values
  ('dddddddd-0000-0000-0000-00000000d001', 'SELLER'),
  ('dddddddd-0000-0000-0000-00000000d001', 'BUYER')
on conflict do nothing;

do $$
begin
  if (select count(*) from user_roles
      where user_id = 'dddddddd-0000-0000-0000-00000000d001') < 2 then
    raise exception 'Multi-role model failed';
  end if;
end;
$$;

-- 4) Property ≠ Listing: two listings can share one property
insert into properties (id, property_type, area_sqm, city, province)
values ('eeeeeeee-0000-0000-0000-000000000001', 'APARTMENT', 80, 'تهران', 'تهران')
on conflict (id) do nothing;

insert into listings (id, property_id, seller_id, deal_type, price_rial, status)
values
  ('ffffffff-0000-0000-0000-000000000001', 'eeeeeeee-0000-0000-0000-000000000001',
   'dddddddd-0000-0000-0000-00000000d001', 'SALE', 9000000000, 'DRAFT'),
  ('ffffffff-0000-0000-0000-000000000002', 'eeeeeeee-0000-0000-0000-000000000001',
   'dddddddd-0000-0000-0000-00000000d001', 'RENT', 900000000, 'DRAFT')
on conflict (id) do nothing;

do $$
begin
  if (select count(*) from listings where property_id = 'eeeeeeee-0000-0000-0000-000000000001') < 2 then
    raise exception 'Property/Listing separation failed';
  end if;
end;
$$;

-- 5) Double-booking guard
insert into listings (id, property_id, seller_id, deal_type, price_rial, status)
values ('ffffffff-0000-0000-0000-000000000003', 'eeeeeeee-0000-0000-0000-000000000001',
        'dddddddd-0000-0000-0000-00000000d001', 'SALE', 1000000000, 'ACTIVE')
on conflict (id) do nothing;

insert into visits (listing_id, buyer_id, slot_start, slot_end, status)
values (
  'ffffffff-0000-0000-0000-000000000003',
  'dddddddd-0000-0000-0000-00000000d001',
  now() + interval '1 day',
  now() + interval '1 day' + interval '30 minutes',
  'CONFIRMED'
);

do $$
begin
  begin
    insert into visits (listing_id, buyer_id, slot_start, slot_end, status)
    values (
      'ffffffff-0000-0000-0000-000000000003',
      'dddddddd-0000-0000-0000-00000000d001',
      now() + interval '1 day',
      now() + interval '1 day' + interval '30 minutes',
      'CONFIRMED'
    );
    raise exception 'Double booking was allowed — unique index missing';
  exception when unique_violation then
    null; -- expected
  end;
end;
$$;

-- 6) listing_prices view computes price/m²
do $$
declare
  pps bigint;
begin
  select price_per_sqm_rial into pps
  from listing_prices
  where listing_id = 'bbbbbbbb-0000-0000-0000-000000000001';

  if pps is null or pps <= 0 then
    raise exception 'listing_prices.price_per_sqm_rial expected > 0, got %', pps;
  end if;
end;
$$;

-- 7) DEV seed present and labeled
do $$
declare
  n int;
begin
  select count(*) into n from listings where data_source = 'DEV_FIXTURE';
  if n < 1 then
    raise exception 'DEV_FIXTURE seed missing';
  end if;
end;
$$;

-- 8) audit_logs is append-only
do $$
begin
  begin
    update audit_logs set action = action where id = 1;
    if found then
      raise exception 'audit_logs UPDATE should be blocked';
    end if;
  exception when raise_exception then
    if sqlerrm not like '%append-only%' then
      raise;
    end if;
  end;
end;
$$;

-- 9) Status transition writes history + published_at
update listings
set status = 'ACTIVE'
where id = 'ffffffff-0000-0000-0000-000000000001';

do $$
begin
  if not exists (
    select 1 from listing_status_history
    where listing_id = 'ffffffff-0000-0000-0000-000000000001'
      and to_status = 'ACTIVE'
  ) then
    raise exception 'listing_status_history not written on transition';
  end if;

  if not exists (
    select 1 from listings
    where id = 'ffffffff-0000-0000-0000-000000000001'
      and published_at is not null
  ) then
    raise exception 'published_at not set on activation';
  end if;
end;
$$;

-- 10) Freshness function
select public.refresh_listing_freshness();

commit;

select 'SCHEMA_SMOKE_OK' as result;
