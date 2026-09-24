# 4Divari — Architecture Decision Record (ADR)

**Status:** Accepted for Phase 1  
**Date:** 2026-09-24  
**Context:** Greenfield Android + Supabase real-estate marketplace for Iran, expandable regionally.

---

## D-001 — CI-first build verification

**Decision:** GitHub Actions is the authoritative build/test/lint gate. The authoring sandbox cannot reach Google Maven / Gradle distributions (network policy).

**Consequences:**  
- Every change is pushed to the session branch; CI runs `test → detekt → lint → assembleDebug`.  
- Contributors with normal network use `./gradlew` locally — wrapper is committed.  
- Release APK gate runs only on `main`.

## D-002 — Multi-module Feature + Clean Architecture

**Decision:** `app` / `core/*` / `feature/*` Gradle modules with strict dependency direction:

```
feature → core → (no feature imports)
app → all features + core
```

**Why:** Backend swap without UI rewrite; compile-time isolation; smaller incremental builds.

## D-003 — Unified result & UI state machines

**Decision:** Repositories return `AppResult` (`Success | Empty | Failure(AppError)`). ViewModels expose `UiState` (`Idle | Loading | Content | Empty | Error`). One renderer (`UiStateRenderer`) enforces Loading/Empty/Error/Offline/Retry UX.

**Why:** Master prompt §38 — no screen may end with "Something went wrong". Error taxonomy is actionable (Offline vs Server vs Validation…).

## D-004 — Environment + DataSource labeling (anti-prototype)

**Decision:** `AppConfig.environment ∈ {PRODUCTION, STAGING, DEVELOPMENT}` with hard `require()` guards (no network logs / dev overlay in PRODUCTION). Every domain payload can carry `DataSource ∈ {REAL, DEV_FIXTURE, PLACEHOLDER}`; UI badges fixtures in dev builds.

**Why:** Master prompt §3 / §53 — mock must never be mistakable for production. Home rails ship with `isImplemented=false` → "به‌زودی", not fake listings.

## D-005 — RTL-first, Persian default

**Decision:** Root forces `LayoutDirection.Rtl`. Typography = bundled Vazirmatn (OFL variable font). `values/` = Persian, `values-en/` = English skeleton with key parity. Backend strings stay neutral; formatting (`Format.price`, Persian digits) happens at the UI edge.

**Why:** Master prompt §31 — RTL is not a late add-on.

## D-006 — Role model is many-to-many from day one

**Decision:** `roles` + `user_roles` tables (SQL in DATABASE_SCHEMA.md). Navigation exposes `CustomerTab` set today; Agent tab set is a later swap of the same `NavHost`.

**Why:** Never `user.role = "AGENT"` scalar (master prompt §6).

## D-007 — Property ≠ Listing

**Decision:** Schema separates immutable-ish `properties` from commercial `listings` (price, status, parties, verification, freshness).

**Why:** One property may be listed multiple times by different parties — core marketplace invariant (master prompt §8).

## D-008 — Analytics schema-first

**Decision:** `AnalyticsEvent` sealed types with fixed names from master prompt §39. Tracker is an interface; Phase 1 ships in-memory implementation (buffered, testable), swap to backend collector later.

**Why:** Event contract stable before vendor SDK choice; privacy = no PII in params.

## D-009 — Deferred on purpose (not forgotten)

Room, Maps SDK, Paging, Supabase SDK, AI tool-calling, payment, 3D — all have explicit seams (`core:network`, repositories, `Routes` constants) but are **not** scaffolded empty in Phase 1 (avoid over-engineering, master prompt goals).

---

## Module graph

```
                    ┌──────────── app ────────────┐
                    │  Hilt root, NavHost, theme  │
                    └──────┬──────────┬───────────┘
           feature:home    │          │  feature:search/saved/profile
           ┌───────────────┘          └───────────────┐
           ▼                                          ▼
   ┌───────────────────────────────────────────────────────┐
   │ core:ui → core:designsystem → (compose)              │
   │ core:navigation                                      │
   │ core:analytics                                       │
   │ core:network → core:environment, core:common         │
   └───────────────────────────────────────────────────────┘
```

## Error flow

```
Retrofit Response / Throwable
        ↓ ErrorMapper
   AppError (Offline|Server|Client|Unauthorized|Validation|Serialization|RateLimited|Unexpected)
        ↓ AppResult.Failure
   ViewModel → UiState.Error(canRetry)
        ↓ UiStateRenderer
   OfflineState | ErrorState(+retry) | EmptyState | LoadingState | Content
```
