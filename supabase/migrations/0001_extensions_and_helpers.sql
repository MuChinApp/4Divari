-- 0001: extensions, helper functions, updated_at trigger
-- Language-neutral backend; timestamps UTC.

create extension if not exists "pgcrypto";

-- Supabase provides auth.uid(); for plain Postgres CI we define a stub
-- that can be overridden. On real Supabase, auth schema wins.
create or replace function public.uid()
returns uuid
language sql
stable
as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;

-- Fallback used only when auth.uid() is absent (local CI without Supabase).
create or replace function public.current_user_id()
returns uuid
language sql
stable
as $$
  select coalesce(
    nullif(current_setting('request.jwt.claim.sub', true), '')::uuid,
    null
  );
$$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

comment on function public.set_updated_at() is
  'BEFORE UPDATE trigger helper: keeps updated_at authoritative.';
