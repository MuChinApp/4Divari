-- Grants for non-owner role so RLS actually applies (CI + Supabase-like).
-- Superuser/owner bypass RLS; clients always use a lesser role.

do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'app_user') then
    create role app_user nologin;
  end if;
end;
$$;

grant usage on schema public to app_user;
grant all on all tables in schema public to app_user;
grant all on all sequences in schema public to app_user;
grant execute on all functions in schema public to app_user;
alter default privileges in schema public grant all on tables to app_user;
alter default privileges in schema public grant all on sequences to app_user;
alter default privileges in schema public grant execute on functions to app_user;

-- Force RLS even for table owner (Supabase applies policies to API roles;
-- FORCE keeps CI honest if someone connects as owner by mistake).
do $$
declare r record;
begin
  for r in
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind = 'r' and c.relrowsecurity
  loop
    execute format('alter table public.%I force row level security', r.relname);
  end loop;
end;
$$;
