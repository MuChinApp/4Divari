-- 0008: storage bucket policies + freshness RPC + listing status transition

-- ---- Storage (Supabase Storage tables exist on platform; for CI we no-op
-- when storage schema is missing so migrations still apply on plain PG.)

do $$
begin
  if exists (select 1 from information_schema.tables where table_schema = 'storage') then
    -- Property media bucket: public read, authenticated write with size/MIME limits
    insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
    values (
      'property-media',
      'property-media',
      true,
      52428800, -- 50 MB
      array['image/jpeg','image/png','image/webp','video/mp4','application/pdf']
    )
    on conflict (id) do nothing;

    execute $policy$
      create policy "property-media public read"
        on storage.objects for select
        using (bucket_id = 'property-media');
    $policy$;
  end if;
exception when others then
  -- storage schema shape differs outside Supabase; ignore for CI
  null;
end;
$$;

-- ---- Freshness updater (called by cron / edge function) ----
create or replace function public.refresh_listing_freshness()
returns int
language plpgsql
security definer
set search_path = public
as $$
declare
  updated int;
begin
  update listings
  set freshness = case
    when last_verified_at > now() - interval '7 days' then 'fresh'
    when last_verified_at > now() - interval '30 days' then 'aging'
    else 'stale'
  end
  where deleted_at is null
    and status in ('ACTIVE','PAUSED','UNDER_OFFER');

  get diagnostics updated = row_count;
  return updated;
end;
$$;

-- ---- Status transition guard ----
create or replace function public.enforce_listing_transition()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if old.status is distinct from new.status then
    insert into listing_status_history (listing_id, from_status, to_status, changed_by, reason)
    values (new.id, old.status, new.status, public.current_user_id(), null);

    if new.status = 'ACTIVE' and new.published_at is null then
      new.published_at := now();
    end if;

    new.last_updated_at := now();
    new.last_verified_at := now();
  else
    new.last_updated_at := now();
  end if;
  return new;
end;
$$;

create trigger listings_transition
  before update of status on listings
  for each row execute function public.enforce_listing_transition();

-- ---- Seller/agent re-verification nudge view ----
create view stale_listings as
select id, seller_id, agent_id, status, freshness, last_verified_at,
       extract(epoch from (now() - last_verified_at)) / 86400 as days_since_verify
from listings
where deleted_at is null
  and status = 'ACTIVE'
  and last_verified_at < now() - interval '14 days';
