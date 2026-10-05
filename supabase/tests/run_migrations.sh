#!/usr/bin/env bash
# Apply all migrations + run smoke tests against a PostgreSQL instance.
# Usage: DB_URL=postgresql://... ./supabase/tests/run_migrations.sh
set -euo pipefail

DB_URL="${DB_URL:?DB_URL is required}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MIG_DIR="$ROOT/supabase/migrations"

echo "== Applying migrations from $MIG_DIR =="
for f in "$MIG_DIR"/*.sql; do
  echo "  -> $(basename "$f")"
  psql "$DB_URL" -v ON_ERROR_STOP=1 -q -f "$f"
done

echo "== Schema smoke =="
psql "$DB_URL" -v ON_ERROR_STOP=1 -q -f "$ROOT/supabase/tests/schema_smoke.sql"

echo "== RLS smoke =="
psql "$DB_URL" -v ON_ERROR_STOP=1 -q -f "$ROOT/supabase/tests/rls_smoke.sql"

echo "ALL_BACKEND_TESTS_OK"
