-- 00090: DEV seed — clearly labeled fixtures for local/staging only.
-- Every row: data_source = 'DEV_FIXTURE'. Never run against PRODUCTION.

do $$
begin
  if current_setting('app.environment', true) = 'production' then
    raise exception 'Seed refused: app.environment=production';
  end if;
end;
$$;

-- Dev users (phone format valid; no real people)
insert into users (id, phone_e164, phone_verified_at, status) values
  ('11111111-1111-1111-1111-111111111111', '+989110000001', now(), 'active'),
  ('22222222-2222-2222-2222-222222222222', '+989110000002', now(), 'active'),
  ('33333333-3333-3333-3333-333333333333', '+989110000003', now(), 'active')
on conflict (phone_e164) do nothing;

insert into profiles (user_id, display_name) values
  ('11111111-1111-1111-1111-111111111111', 'کاربر توسعه - فروشنده'),
  ('22222222-2222-2222-2222-222222222222', 'کاربر توسعه - خریدار'),
  ('33333333-3333-3333-3333-333333333333', 'کاربر توسعه - مشاور')
on conflict (user_id) do nothing;

insert into user_roles (user_id, role) values
  ('11111111-1111-1111-1111-111111111111', 'SELLER'),
  ('11111111-1111-1111-1111-111111111111', 'LANDLORD'),
  ('22222222-2222-2222-2222-222222222222', 'BUYER'),
  ('33333333-3333-3333-3333-333333333333', 'AGENT')
on conflict do nothing;

-- Dev property + listing
insert into properties (
  id, property_type, area_sqm, bedrooms, floor, total_floors, build_year,
  has_elevator, has_parking, has_storage, city, province, neighborhood,
  latitude, longitude, description
) values (
  'aaaaaaaa-0000-0000-0000-000000000001',
  'APARTMENT', 90, 2, 3, 5, 1401,
  true, true, true,
  'تهران', 'تهران', 'پونک',
  35.77, 51.38,
  'فایل توسعه — داده ساختگی با برچسب DEV_FIXTURE'
)
on conflict (id) do nothing;

insert into property_addresses (property_id, province, city, neighborhood, street) values (
  'aaaaaaaa-0000-0000-0000-000000000001',
  'تهران', 'تهران', 'پونک', 'ستارخان'
) on conflict (property_id) do nothing;

insert into listings (
  id, property_id, seller_id, deal_type, price_rial, status,
  published_at, data_source, verification_status
) values (
  'bbbbbbbb-0000-0000-0000-000000000001',
  'aaaaaaaa-0000-0000-0000-000000000001',
  '11111111-1111-1111-1111-111111111111',
  'SALE', 12800000000, 'ACTIVE',
  now(), 'DEV_FIXTURE', 'unverified'
) on conflict (id) do nothing;

insert into property_media (
  property_id, storage_path, media_type, mime_type, byte_size, sort_order, is_cover, data_source
) values (
  'aaaaaaaa-0000-0000-0000-000000000001',
  'dev/fixture-apartment-cover.jpg',
  'image', 'image/jpeg', 102400, 0, true, 'DEV_FIXTURE'
) on conflict (storage_path) do nothing;

insert into audit_logs (actor_id, action, entity_type, entity_id, after)
values (
  '11111111-1111-1111-1111-111111111111',
  'seed.dev_fixture',
  'listings',
  'bbbbbbbb-0000-0000-0000-000000000001',
  jsonb_build_object('data_source', 'DEV_FIXTURE')
);
