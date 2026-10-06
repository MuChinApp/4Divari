# 4Divari — چاردیواری

> **«چهار دیواری، جایی برای شروع یک معامله مطمئن.»**

Real-estate marketplace platform for Iran — discovery, trust, and decision support (not just listings).

## Status

**Phase 1 — Foundation** (see `docs/PHASE_0_AUDIT.md` for audit, `docs/ROADMAP.md` for phases).

| Layer | State |
|-------|--------|
| App skeleton, design system, RTL, nav, network, DI, env, analytics, CI | ✅ **CI green** |
| Backend (Supabase, auth, RLS) | ✅ migrations + RLS smoke tests in CI |
| Marketplace feeds / map / detail | ✅ MVP |
| Seller / Agent / Chat / Trust / AI | ✅ MVP (phases 0–8) |

## Install & test (APK)

CI can build installable APKs on demand (`Actions → APK → Run workflow`):

- `4Divari-<version>-debug.apk` — debug build (`ir.chardivari.app.debug`), best for QA/debugging
- `4Divari-<version>-release.apk` — minified release build (`ir.chardivari.app`), production-like

They are attached to the run's artifacts and to a GitHub Release (default tag `test-build`),
so you can download the file in a browser (phone included) and sideload it
(allow «install unknown apps» for your browser/file manager). minSdk 24 (Android 7+).

Release builds are signed: with `-P4divari.releaseStoreFile=...` keystore properties if
provided, otherwise with the debug key — installable for testing, not for Play upload.

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
