# 4Divari — چاردیواری

> **«چهار دیواری، جایی برای شروع یک معامله مطمئن.»**

Real-estate marketplace platform for Iran — discovery, trust, and decision support (not just listings).

## Status

**Phase 1 — Foundation** (see `docs/PHASE_0_AUDIT.md` for audit, `docs/ROADMAP.md` for phases).

| Layer | State |
|-------|--------|
| App skeleton, design system, RTL, nav, network, DI, env, analytics, CI | ✅ |
| Backend (Supabase, auth, RLS) | ⬜ Phase 2 |
| Marketplace feeds / map / detail | ⬜ Phase 3 |
| Seller / Agent / Chat / Trust / AI | ⬜ Phases 4–8 |

## Architecture (short)

Multi-module Android: `app` + `core/*` + `feature/*`.  
`AppResult`/`UiState` state machines, Hilt, Retrofit, Material 3 + Vazirmatn, forced RTL.  
Full ADRs: `docs/ARCHITECTURE.md`. Schema: `docs/DATABASE_SCHEMA.md`.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI (GitHub Actions) runs the same on every push — **CI is the source of truth** for green builds.

Environment overrides (gradle properties):

```
-P4divari.environment=DEVELOPMENT|STAGING|PRODUCTION
-P4divari.apiBaseUrl=https://...
```

## Data honesty rules

- `DataSource.REAL | DEV_FIXTURE | PLACEHOLDER` on every payload  
- Unimplemented rails show «به‌زودی», never fake listings  
- No mock OTP / no hardcoded tokens — ever  

## License / brand

Product name **4Divari / چاردیواری**. Font: Vazirmatn (OFL) — see `docs/licenses/OFL-Vazirmatn.txt`.
