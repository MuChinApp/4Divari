-- 00096: Trust tooling (Phase 7) — reporting, freshness confirmation,
-- verification lifecycle, admin moderation queue, fraud signals.
--
-- Ground rules:
--   * every trust signal is computed from real data (no invented scores);
--   * flags carry evidence + uncertainty (PRICE_UNKNOWN when samples < 8);
--   * moderation actions are SECURITY DEFINER + admin-checked + audited;
--   * notification titles are language-neutral codes (Phase 6 rule).

-- ---- 1) One open report per (listing, reporter) ----
create unique index if not exists idx_reports_one_open
  on reports (listing_id, reporter_id)
  where status in ('open', 'reviewing');

-- ---- 2) Internal notifier (definer, kept private) ----
create or replace function public.trust_notify(
  p_user_id uuid,
  p_title text,
  p_body text,
  p_data jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_user_id is null then
    return;
  end if;
  insert into notifications (user_id, type, title, body, data)
  values (
    p_user_id,
    'trust',
    p_title,
    left(p_body, 2000),
    p_data
  );
end;
$$;

revoke execute on function public.trust_notify(uuid, text, text, jsonb) from public;

-- ---- 3) Report a listing (logged-in, no self-reports, no open dupes) ----
create or replace function public.report_listing(
  p_listing_id uuid,
  p_reason text,
  p_detail text default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_id uuid;
  v_seller uuid;
  v_agent uuid;
  v_open uuid;
  v_admin uuid;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;
  if p_reason is null or char_length(btrim(p_reason)) not between 3 and 500 then
    raise exception 'reason must be 3..500 characters';
  end if;

  select seller_id, agent_id into v_seller, v_agent
  from listings
  where id = p_listing_id and deleted_at is null;
  if not found then
    raise exception 'listing not found';
  end if;
  if v_uid in (v_seller, v_agent) then
    raise exception 'cannot report own listing';
  end if;

  select id into v_open
  from reports
  where listing_id = p_listing_id
    and reporter_id = v_uid
    and status in ('open', 'reviewing')
  limit 1;
  if v_open is not null then
    raise exception 'duplicate report: an open report already exists';
  end if;

  insert into reports (listing_id, reporter_id, reason, detail)
  values (p_listing_id, v_uid, btrim(p_reason), nullif(btrim(coalesce(p_detail, '')), ''))
  returning id into v_id;

  -- Notify every admin so the queue does not depend on polling.
  for v_admin in
    select user_id from user_roles where role = 'ADMIN'
  loop
    perform public.trust_notify(
      v_admin,
      'report_filed',
      left(btrim(p_reason), 200),
      jsonb_build_object('report_id', v_id, 'listing_id', p_listing_id)
    );
  end loop;

  return v_id;
end;
$$;

comment on function public.report_listing(uuid, text, text) is
  'SECURITY DEFINER: logged-in report with self-report and open-dupe guards.';

-- ---- 4) Freshness confirmation (party-only re-verify nudge) ----
create or replace function public.confirm_listing(p_listing_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_freshness text;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  update listings
    set last_verified_at = now(),
        freshness = 'fresh'
  where id = p_listing_id
    and deleted_at is null
    and (seller_id = v_uid or agent_id = v_uid)
  returning freshness into v_freshness;
  if not found then
    raise exception 'not a listing party';
  end if;

  insert into audit_logs (actor_id, action, entity_type, entity_id, after)
  values (v_uid, 'listing.confirmed', 'listings', p_listing_id::text,
          jsonb_build_object('freshness', v_freshness));

  return v_freshness;
end;
$$;

comment on function public.confirm_listing(uuid) is
  'SECURITY DEFINER: seller/agent re-confirms the listing is still accurate (freshness reset).';

-- ---- 5) Seller requests verification (unverified/rejected -> pending) ----
create or replace function public.request_listing_verification(p_listing_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_status text;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  select verification_status into v_status
  from listings
  where id = p_listing_id and deleted_at is null;
  if not found then
    raise exception 'listing not found';
  end if;
  if not exists (
    select 1 from listings
    where id = p_listing_id
      and (seller_id = v_uid or agent_id = v_uid)
  ) then
    raise exception 'not a listing party';
  end if;

  if v_status = 'pending' or v_status = 'verified' then
    return v_status;
  end if;

  update listings
    set verification_status = 'pending'
  where id = p_listing_id;

  insert into property_verifications (listing_id, kind, status)
  values (p_listing_id, 'completeness', 'pending')
  on conflict do nothing;

  insert into audit_logs (actor_id, action, entity_type, entity_id, after)
  values (v_uid, 'listing.verification_requested', 'listings', p_listing_id::text,
          jsonb_build_object('verification_status', 'pending'));

  return 'pending';
end;
$$;

comment on function public.request_listing_verification(uuid) is
  'SECURITY DEFINER: listing party requests review; unverified/rejected -> pending (idempotent).';

-- ---- 6) Admin decides verification (also notifies the owner) ----
create or replace function public.admin_set_listing_verification(
  p_listing_id uuid,
  p_status text
)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_owner uuid;
  v_target text;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;
  if not public.is_admin() then
    raise exception 'admin only';
  end if;
  if p_status not in ('verified', 'rejected') then
    raise exception 'status must be verified or rejected';
  end if;
  v_target := p_status;

  update listings
    set verification_status = v_target
  where id = p_listing_id and deleted_at is null;
  if not found then
    raise exception 'listing not found';
  end if;

  insert into property_verifications (listing_id, kind, status, reviewed_by, verified_at)
  values (
    p_listing_id,
    'completeness',
    case when v_target = 'verified' then 'passed' else 'failed' end,
    v_uid,
    case when v_target = 'verified' then now() else null end
  );

  select coalesce(seller_id, agent_id) into v_owner
  from listings where id = p_listing_id;

  perform public.trust_notify(
    v_owner,
    'verification_status',
    null,
    jsonb_build_object('listing_id', p_listing_id, 'verification_status', v_target)
  );

  insert into audit_logs (actor_id, action, entity_type, entity_id, after)
  values (v_uid, 'listing.verification_decided', 'listings', p_listing_id::text,
          jsonb_build_object('verification_status', v_target));

  return v_target;
end;
$$;

comment on function public.admin_set_listing_verification(uuid, text) is
  'SECURITY DEFINER: admin verification decision + owner notification + audit.';

-- ---- 7) Fraud signals: computed, evidence-based, uncertainty-aware ----
create or replace function public.listing_risk_signals(p_listing_id uuid)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_row record;
  v_flags jsonb := '[]'::jsonb;
  v_open int;
  v_samples int;
  v_median numeric;
  v_pps numeric;
  v_ratio numeric;
  v_days numeric;
begin
  select l.id, l.deal_type, l.price_rial, l.freshness, l.last_verified_at, p.area_sqm, p.city
  into v_row
  from listings l
  join properties p on p.id = l.property_id
  where l.id = p_listing_id and l.deleted_at is null;
  if not found then
    raise exception 'listing not found';
  end if;

  select count(*) into v_open
  from reports
  where listing_id = p_listing_id
    and status in ('open', 'reviewing');
  if v_open > 0 then
    v_flags := v_flags || jsonb_build_array(jsonb_build_object(
      'code', 'OPEN_REPORTS',
      'severity', 'high',
      'evidence', jsonb_build_object('count', v_open)
    ));
  end if;

  if v_row.last_verified_at is null or v_row.last_verified_at < now() - interval '30 days'
     or v_row.freshness = 'stale'
  then
    v_days := coalesce(
      extract(epoch from (now() - v_row.last_verified_at)) / 86400,
      9999
    );
    v_flags := v_flags || jsonb_build_array(jsonb_build_object(
      'code', 'STALE_DATA',
      'severity', 'medium',
      'evidence', jsonb_build_object('days_since_verify', round(v_days, 1))
    ));
  end if;

  -- Price per sqm vs same-city SALE median; only when we have enough peers.
  if v_row.deal_type = 'SALE' and v_row.area_sqm > 0 and v_row.price_rial > 0 then
    select count(*), percentile_cont(0.5) within group (
      order by (l.price_rial::numeric / nullif(p.area_sqm, 0))
    )
    into v_samples, v_median
    from listings l
    join properties p on p.id = l.property_id
    where l.status = 'ACTIVE'
      and l.deleted_at is null
      and l.deal_type = 'SALE'
      and l.id <> v_row.id
      and p.area_sqm > 0
      and l.price_rial > 0
      and p.city = v_row.city;

    if v_samples >= 8 and v_median is not null and v_median > 0 then
      v_pps := v_row.price_rial::numeric / v_row.area_sqm;
      v_ratio := v_pps / v_median;
      if v_ratio > 2.5 then
        v_flags := v_flags || jsonb_build_array(jsonb_build_object(
          'code', 'PRICE_OUTLIER_HIGH',
          'severity', 'high',
          'evidence', jsonb_build_object(
            'ratio_to_median', round(v_ratio, 2),
            'price_per_sqm', round(v_pps, 0),
            'median_per_sqm', round(v_median, 0),
            'samples', v_samples
          )
        ));
      elsif v_ratio < 0.4 then
        v_flags := v_flags || jsonb_build_array(jsonb_build_object(
          'code', 'PRICE_OUTLIER_LOW',
          'severity', 'medium',
          'evidence', jsonb_build_object(
            'ratio_to_median', round(v_ratio, 2),
            'price_per_sqm', round(v_pps, 0),
            'median_per_sqm', round(v_median, 0),
            'samples', v_samples
          )
        ));
      end if;
    else
      v_flags := v_flags || jsonb_build_array(jsonb_build_object(
        'code', 'PRICE_UNKNOWN',
        'severity', 'info',
        'evidence', jsonb_build_object('samples', v_samples, 'reason', 'insufficient_peers')
      ));
    end if;
  end if;

  return jsonb_build_object(
    'listing_id', p_listing_id,
    'flags', v_flags,
    'assessed_at', now()
  );
end;
$$;

comment on function public.listing_risk_signals(uuid) is
  'SECURITY DEFINER: evidence-based flags (reports, staleness, price outliers) with uncertainty.';

-- ---- 8) Moderation queue (admin, one call) ----
create or replace function public.moderation_queue()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_reports jsonb;
  v_verifications jsonb;
  v_open int;
  v_pending int;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;
  if not public.is_admin() then
    raise exception 'admin only';
  end if;

  select coalesce(jsonb_agg(item order by ord), '[]'::jsonb)
  into v_reports
  from (
    select
      row_number() over (order by r.created_at asc) as ord,
      jsonb_build_object(
        'id', r.id,
        'listing_id', r.listing_id,
        'reason', r.reason,
        'detail', r.detail,
        'status', r.status,
        'created_at', r.created_at,
        'reporter_id', r.reporter_id,
        'listing', jsonb_build_object(
          'city', p.city,
          'neighborhood', p.neighborhood,
          'area_sqm', p.area_sqm,
          'price_rial', l.price_rial,
          'deal_type', l.deal_type,
          'verification_status', l.verification_status,
          'freshness', l.freshness
        ),
        'risk', public.listing_risk_signals(r.listing_id) -> 'flags'
      ) as item
    from reports r
    left join listings l on l.id = r.listing_id
    left join properties p on p.id = l.property_id
    where r.status in ('open', 'reviewing')
    order by r.created_at asc
    limit 100
  ) q;

  select coalesce(jsonb_agg(item order by ord), '[]'::jsonb)
  into v_verifications
  from (
    select
      row_number() over (
        order by l.published_at asc nulls last
      ) as ord,
      jsonb_build_object(
        'listing_id', l.id,
        'verification_status', l.verification_status,
        'seller_id', l.seller_id,
        'agent_id', l.agent_id,
        'published_at', l.published_at,
        'listing', jsonb_build_object(
          'city', p.city,
          'neighborhood', p.neighborhood,
          'area_sqm', p.area_sqm,
          'price_rial', l.price_rial,
          'deal_type', l.deal_type,
          'verification_status', l.verification_status,
          'freshness', l.freshness
        ),
        'risk', public.listing_risk_signals(l.id) -> 'flags'
      ) as item
    from listings l
    join properties p on p.id = l.property_id
    where l.verification_status = 'pending'
      and l.deleted_at is null
    order by l.published_at asc nulls last
    limit 100
  ) v;

  select count(*) into v_open from reports where status in ('open', 'reviewing');
  select count(*) into v_pending from listings
    where verification_status = 'pending' and deleted_at is null;

  return jsonb_build_object(
    'counts', jsonb_build_object(
      'open_reports', v_open,
      'pending_verifications', v_pending
    ),
    'reports', v_reports,
    'verifications', v_verifications,
    'generated_at', now()
  );
end;
$$;

comment on function public.moderation_queue() is
  'SECURITY DEFINER: open reports + pending verifications + risk flags in one admin call.';

-- ---- 9) Report resolved -> notify the reporter ----
create or replace function public.notify_on_report_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if old.status in ('open', 'reviewing')
     and new.status in ('actioned', 'dismissed')
     and old.status is distinct from new.status
  then
    perform public.trust_notify(
      new.reporter_id,
      'report_status',
      null,
      jsonb_build_object(
        'report_id', new.id,
        'listing_id', new.listing_id,
        'report_status', new.status
      )
    );
  end if;
  return new;
end;
$$;

drop trigger if exists notify_on_report_update on reports;
create trigger notify_on_report_update
  after update of status on reports
  for each row
  execute function public.notify_on_report_update();

revoke execute on function public.notify_on_report_update() from public;

-- ---- 10) Grants (anon intentionally excluded — trust actions need a session) ----
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function public.report_listing(uuid, text, text) to authenticated;
    grant execute on function public.confirm_listing(uuid) to authenticated;
    grant execute on function public.request_listing_verification(uuid) to authenticated;
    grant execute on function public.admin_set_listing_verification(uuid, text) to authenticated;
    grant execute on function public.listing_risk_signals(uuid) to authenticated;
    grant execute on function public.moderation_queue() to authenticated;
  end if;
  if exists (select 1 from pg_roles where rolname = 'app_user') then
    grant execute on function public.report_listing(uuid, text, text) to app_user;
    grant execute on function public.confirm_listing(uuid) to app_user;
    grant execute on function public.request_listing_verification(uuid) to app_user;
    grant execute on function public.admin_set_listing_verification(uuid, text) to app_user;
    grant execute on function public.listing_risk_signals(uuid) to app_user;
    grant execute on function public.moderation_queue() to app_user;
  end if;
end;
$$;
