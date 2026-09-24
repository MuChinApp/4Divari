-- 00092: Phase 3 — auth user sync (Supabase) + listing contact RPC.
-- Favorites/saved_searches FK public.users(id) must match GoTrue auth.users.id.

-- ---- Sync auth.users -> public.users (only when Supabase auth schema exists) ----
do $$
begin
  if exists (
    select 1 from information_schema.tables
    where table_schema = 'auth' and table_name = 'users'
  ) then
    create or replace function public.handle_new_user()
    returns trigger
    language plpgsql
    security definer
    set search_path = public
    as $fn$
    begin
      insert into public.users (id, phone_e164, phone_verified_at, status)
      values (
        new.id,
        coalesce(new.phone, new.email, 'unknown@invalid'),
        now(),
        'active'
      )
      on conflict (id) do nothing;
      return new;
    end;
    $fn$;

    drop trigger if exists on_auth_user_created on auth.users;
    create trigger on_auth_user_created
      after insert on auth.users
      for each row
      execute function public.handle_new_user();
  end if;
exception when others then
  -- auth schema shape differs outside managed Supabase; ignore for CI
  null;
end;
$$;

-- ---- Contact channel for an ACTIVE (or historically closed) listing ----
-- Returns seller/agent phone without exposing the full users table via RLS.
create or replace function public.listing_contact(p_listing_id uuid)
returns table (
  phone_e164 text,
  display_name text,
  party_role text
)
language sql
stable
security definer
set search_path = public
as $$
  select
    u.phone_e164,
    coalesce(nullif(p.display_name, ''),
             case when l.agent_id is not null then 'مشاور' else 'مالک' end),
    case when l.agent_id is not null then 'agent' else 'seller' end
  from listings l
  join users u on u.id = coalesce(l.agent_id, l.seller_id)
  left join profiles p on p.user_id = u.id
  where l.id = p_listing_id
    and l.deleted_at is null
    and l.status in ('ACTIVE', 'UNDER_OFFER', 'SOLD', 'RENTED')
    and u.deleted_at is null;
$$;

comment on function public.listing_contact(uuid) is
  'SECURITY DEFINER: phone + display name for contacting a listing party. Read-only.';

grant execute on function public.listing_contact(uuid) to anon;
grant execute on function public.listing_contact(uuid) to authenticated;
grant execute on function public.listing_contact(uuid) to app_user;
