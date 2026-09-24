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

reset role;
rollback;

select 'RLS_SMOKE_OK' as result;
