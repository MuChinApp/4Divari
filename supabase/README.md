# 4Divari backend — local & CI

## Layout

- `migrations/` — ordered SQL (`0001`…`00091`); language-neutral, UTC timestamps.
- `tests/run_migrations.sh` — applies every migration, then runs smoke tests.
- `tests/schema_smoke.sql` — structure, multi-role, Property≠Listing, double-booking guard,
  price/m² view, DEV seed label, append-only audit, status history.
- `tests/rls_smoke.sql` — as non-owner `app_user` with FORCE RLS: self-only users,
  public ACTIVE listings, no role escalation, no cross-user favorites.
- `config.toml` — minimal Supabase project config for `supabase start` (Docker required).

## CI

GitHub Actions job **Postgres migrations + RLS tests** starts `postgres:16`,
runs `DB_URL=postgresql://… ./supabase/tests/run_migrations.sh`.

Plain PostgreSQL (CI) does not include Supabase `auth`/`storage` schemas:

- `0001` defines `public.uid()` / `public.current_user_id()` JWT-claim stubs.
- `0008` storage bucket DDL is wrapped in a DO block that no-ops when `storage` is absent.

## Roles

- `postgres` — migration owner (superuser; bypasses RLS).
- `app_user` — stand-in for Supabase `authenticated`; created in `00091`, FORCE RLS on.

## Functions

- `functions/assistant/index.ts` — Phase 8 AI proxy (OpenAI-compatible).
  Requires an authenticated session; holds the provider key as a secret and
  fails closed (503) when unset. It never executes tools — the client runs
  them against real repositories under the caller's RLS.

Deploy + configure (never commit keys):

```bash
supabase functions deploy assistant
supabase secrets set AI_PROVIDER_URL=https://…/v1/chat/completions \
  AI_PROVIDER_KEY=… AI_MODEL=…
```

## Seed

`00090_seed_dev.sql` — fixtures only, every row `data_source='DEV_FIXTURE'`,
refuses to run when `app.environment=production`.
