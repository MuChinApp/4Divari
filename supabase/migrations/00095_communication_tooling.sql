-- 00095: Phase 6 communication tooling
-- conversation entry RPC, read-receipt policy, notification triggers,
-- realtime publication for supabase changes.

-- ---- 1) participants RLS repair + read receipts ----
-- 0007's participants_self_read used a self-referencing EXISTS (policy on
-- conversation_participants querying conversation_participants) which makes
-- ANY participant read fail with infinite-recursion detection. Clients may
-- read only their OWN participant row; co-participant info (counterpart,
-- unread counts) is served by my_conversations() SECURITY DEFINER below.
drop policy if exists participants_self_read on conversation_participants;
create policy participants_self_read on conversation_participants
  for select using (
    user_id = public.current_user_id()
    or public.is_admin()
  );

drop policy if exists participants_self_update on conversation_participants;
create policy participants_self_update on conversation_participants
  for update
  using (user_id = public.current_user_id())
  with check (user_id = public.current_user_id());

-- ---- 2) Conversation entry: buyer <-> listing counterpart (agent else seller) ----
create or replace function public.start_conversation(p_listing_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_listing record;
  v_counterpart uuid;
  v_conv uuid;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  select id, agent_id, seller_id into v_listing
  from listings
  where id = p_listing_id and deleted_at is null;
  if v_listing.id is null then
    raise exception 'listing not found';
  end if;

  -- Counterpart: the listing's agent unless the caller IS that agent (then seller).
  v_counterpart := case
    when v_listing.agent_id = v_uid then v_listing.seller_id
    else coalesce(v_listing.agent_id, v_listing.seller_id)
  end;
  if v_counterpart is null or v_counterpart = v_uid then
    raise exception 'cannot start a conversation on this listing';
  end if;

  -- Idempotent: one conversation per (listing, pair).
  select c.id into v_conv
  from conversations c
  where c.listing_id = p_listing_id
    and exists (
      select 1 from conversation_participants a
      where a.conversation_id = c.id and a.user_id = v_uid
    )
    and exists (
      select 1 from conversation_participants b
      where b.conversation_id = c.id and b.user_id = v_counterpart
    )
  limit 1;
  if v_conv is not null then
    return v_conv;
  end if;

  insert into conversations (listing_id)
  values (p_listing_id)
  returning id into v_conv;

  insert into conversation_participants (conversation_id, user_id)
  values (v_conv, v_uid), (v_conv, v_counterpart)
  on conflict do nothing;

  return v_conv;
end;
$$;

comment on function public.start_conversation(uuid) is
  'SECURITY DEFINER: find-or-create the (listing, buyer<->counterpart) conversation.';

-- ---- 2b) Conversation list: counterpart + unread + preview in one call ----
create or replace function public.my_conversations()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
  v_out jsonb;
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;

  select coalesce(jsonb_agg(item order by sort_key desc), '[]'::jsonb)
  into v_out
  from (
    select
      jsonb_build_object(
        'conversation_id', c.id,
        'listing_id', c.listing_id,
        'conversation_updated_at', c.updated_at,
        'counterpart_id', other.user_id,
        'my_last_read_at', me.last_read_at,
        'unread_count', (
          select count(*)
          from messages m
          where m.conversation_id = c.id
            and m.deleted_at is null
            and m.sender_id <> v_uid
            and (me.last_read_at is null or m.sent_at > me.last_read_at)
        ),
        'last_message', (
          select jsonb_build_object(
            'body', left(m.body, 160),
            'sent_at', m.sent_at,
            'sender_id', m.sender_id
          )
          from messages m
          where m.conversation_id = c.id
            and m.deleted_at is null
          order by m.sent_at desc
          limit 1
        ),
        'listing_status', l.status,
        'deal_type', l.deal_type,
        'price_rial', l.price_rial,
        'deposit_rial', l.deposit_rial,
        'rent_rial', l.rent_rial,
        'city', pr.city,
        'neighborhood', pr.neighborhood,
        'area_sqm', pr.area_sqm
      ) as item,
      coalesce(
        (select max(m.sent_at) from messages m where m.conversation_id = c.id),
        c.updated_at
      ) as sort_key
    from conversations c
    join conversation_participants me
      on me.conversation_id = c.id and me.user_id = v_uid
    join conversation_participants other
      on other.conversation_id = c.id and other.user_id <> v_uid
    join listings l on l.id = c.listing_id
    join properties pr on pr.id = l.property_id
    where l.deleted_at is null
  ) s;

  return v_out;
end;
$$;

comment on function public.my_conversations() is
  'SECURITY DEFINER: own conversations with counterpart, unread counts and listing summary.';

-- ---- 2c) Read-state writers (server clock — no client time trust) ----
create or replace function public.mark_conversation_read(p_conversation_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;
  update conversation_participants
    set last_read_at = now()
    where conversation_id = p_conversation_id
      and user_id = v_uid;
  if not found then
    raise exception 'not a participant';
  end if;
end;
$$;

create or replace function public.mark_notification_read(p_notification_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := public.current_user_id();
begin
  if v_uid is null then
    raise exception 'authentication required';
  end if;
  update notifications
    set read_at = now()
    where id = p_notification_id
      and user_id = v_uid
      and read_at is null;
end;
$$;

-- ---- 3) Notification triggers (title stores a language-neutral code) ----

create or replace function public.notify_on_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into notifications (user_id, type, title, body, data)
  select
    cp.user_id,
    'message',
    'new_message',
    left(coalesce(new.body, ''), 120),
    jsonb_build_object(
      'conversation_id', new.conversation_id,
      'message_id', new.id,
      'listing_id', c.listing_id
    )
  from conversation_participants cp
  join conversations c on c.id = new.conversation_id
  where cp.conversation_id = new.conversation_id
    and cp.user_id <> new.sender_id;
  return new;
end;
$$;

drop trigger if exists messages_notify on messages;
create trigger messages_notify
  after insert on messages
  for each row execute function public.notify_on_message();

create or replace function public.notify_on_visit_insert()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_notify uuid;
begin
  select coalesce(l.agent_id, l.seller_id) into v_notify
  from listings l where l.id = new.listing_id;

  if v_notify is not null and v_notify <> new.buyer_id then
    insert into notifications (user_id, type, title, body, data)
    values (
      v_notify,
      'visit_requested',
      'visit_requested',
      null,
      jsonb_build_object(
        'visit_id', new.id,
        'listing_id', new.listing_id,
        'slot_start', new.slot_start
      )
    );
  end if;
  return new;
end;
$$;

drop trigger if exists visits_notify_insert on visits;
create trigger visits_notify_insert
  after insert on visits
  for each row execute function public.notify_on_visit_insert();

create or replace function public.notify_on_visit_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if old.status is distinct from new.status
     and new.status in ('CONFIRMED','CANCELLED','RESCHEDULED','COMPLETED') then
    insert into notifications (user_id, type, title, body, data)
    values (
      new.buyer_id,
      'visit_status',
      'visit_status',
      new.status,
      jsonb_build_object(
        'visit_id', new.id,
        'listing_id', new.listing_id,
        'status', new.status,
        'slot_start', new.slot_start
      )
    );
  end if;
  return new;
end;
$$;

drop trigger if exists visits_notify_update on visits;
create trigger visits_notify_update
  after update on visits
  for each row execute function public.notify_on_visit_update();

-- Trigger functions are internal — clients must not call them directly.
revoke execute on function public.notify_on_message() from public;
revoke execute on function public.notify_on_visit_insert() from public;
revoke execute on function public.notify_on_visit_update() from public;

-- ---- 4) Grants for the conversation entry RPC (conditional roles) ----
do $$
begin
  if exists (select 1 from pg_roles where rolname = 'anon') then
    grant execute on function public.start_conversation(uuid) to anon;
    grant execute on function public.my_conversations() to anon;
    grant execute on function public.mark_conversation_read(uuid) to anon;
    grant execute on function public.mark_notification_read(uuid) to anon;
  end if;
  if exists (select 1 from pg_roles where rolname = 'authenticated') then
    grant execute on function public.start_conversation(uuid) to authenticated;
    grant execute on function public.my_conversations() to authenticated;
    grant execute on function public.mark_conversation_read(uuid) to authenticated;
    grant execute on function public.mark_notification_read(uuid) to authenticated;
  end if;
  if exists (select 1 from pg_roles where rolname = 'app_user') then
    grant execute on function public.start_conversation(uuid) to app_user;
    grant execute on function public.my_conversations() to app_user;
    grant execute on function public.mark_conversation_read(uuid) to app_user;
    grant execute on function public.mark_notification_read(uuid) to app_user;
  end if;
end;
$$;

-- ---- 5) Realtime publication (only on Supabase hosts that have it) ----
do $$
declare
  t text;
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    foreach t in array array['messages', 'conversations', 'notifications', 'visits']
    loop
      if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
      ) then
        execute format('alter publication supabase_realtime add table public.%I', t);
      end if;
    end loop;
  end if;
end;
$$;
