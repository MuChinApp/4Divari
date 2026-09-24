# PHASE 0 — Repository Audit Report

**Project:** 4Divari / چاردیواری  
**Date:** 2026-09-24  
**Branch:** `arena/01a0d112-4divari`  
**Auditor:** Agent (Senior Android / Product / Backend / Security hat)

---

## 1. WHAT WAS BUILT (before this session)

| Item | Status |
|------|--------|
| Source code | **None** — repository contained only `.gitignore` (Android template) |
| Gradle project | **None** |
| Modules / architecture | **None** |
| Backend / Supabase | **None** |
| Tests | **None** |
| CI/CD | **None** (no workflows) |
| Issues / PRs / Releases | **Empty** |
| Git history | Single commit: `f9d5064 Initial commit` |
| GitHub metadata | Public repo `MuChinApp/4Divari`, description set, `language: null`, `size: 0` |

**Verdict:** Greenfield. Nothing to preserve, nothing to migrate. No legacy architecture constraints.

## 2. WHAT WAS TESTED

| Check | Result |
|-------|--------|
| `git status` / branch topology | Clean; session branch created from `main@f9d5064` |
| Network reachability (sandbox) | Restricted allowlist — see §4 |
| GitHub Actions availability | **Verified working** — probe run `35945371332` succeeded |
| Push to `origin` | **Verified** — branch push accepted |
| Local Android toolchain | **Absent** — no JDK/SDK/Gradle preinstalled |
| Existing build | **N/A** — no build files existed |

## 3. WHAT FAILED / BLOCKERS DISCOVERED

### 3.1 Environment blockers (sandbox, not product code)

| Blocker | Detail | Mitigation chosen |
|---------|--------|--------------------|
| No local Android SDK / Gradle / Maven access | `dl.google.com`, `services.gradle.org`, `maven.google.com`, `repo.maven.apache.org` all fail TLS (network policy) | **Build & verify on GitHub Actions** (full network); local sandbox used for authoring only |
| No system JDK | `java` not found; apt repos unreachable | JDK 17 runtime fetched via PyPI for any local JVM needs; compilation runs in CI |
| GitHub release-asset CDN blocked | `release-assets.githubusercontent.com` TLS fail | Wrapper JAR + scripts fetched via GitHub Contents API (worked) |
| `gh` token scopes | Integration token: no Actions-admin endpoints (403 on permissions API) | Not needed — push-triggered workflows work on public repo |

### 3.2 Product-critical gaps (expected — nothing existed)

These are the "prototype trap" risks called out in the master prompt. All were **absent**, i.e. no fake OTP, no in-memory repos, no hardcoded listings — clean slate is an advantage.

## 4. INFRASTRUCTURE FINDINGS

```
Reachable from sandbox:   github.com, api.github.com, codeload.github.com,
                          pypi.org, files.pythonhosted.org, registry.npmjs.org
Blocked:                  dl.google.com, maven.google.com, services.gradle.org,
                          repo.maven.apache.org, plugins.gradle.org, raw.githubusercontent.com,
                          release-assets.githubusercontent.com, most public mirrors
```

**Architecture decision (D-001):** CI is the source of truth for compilation, tests, lint, and release gates. Local authoring is verified by pushing to the session branch; GitHub Actions runs `test → detekt → lint → assembleDebug` on every push.

## 5. PROPOSED ARCHITECTURE (adopted for Phase 1)

```
Feature-Based + Clean Architecture (multi-module Gradle)

app/                    → composition root: Hilt, NavHost, theme, BuildConfig env
core/
  common/               → AppResult, AppError, UiState, Format (Persian digits), dispatchers
  environment/          → AppConfig, AppEnvironment, DataSource labeling (anti-fake)
  network/              → Retrofit/OkHttp, ErrorMapper, SafeApi, Hilt network module
  designsystem/         → color/typography/shape tokens, theme, state components, PropertyCard
  ui/                   → UiStateRenderer, PlaceholderScreen
  navigation/           → Routes, CustomerTab (role-ready)
  analytics/            → typed AnalyticsEvent schema + tracker
feature/
  home/ search/ saved/ profile/   → Phase 1 shells (honest placeholders, no fake data)
```

**Why this shape:**
- UI never imports Retrofit/serialization types (dependency rule enforced by modules).
- Backend swap later touches only `core:network` + repositories.
- Role-based nav (Agent tabs) is a tab-set swap, not a rewrite.
- Every async screen maps through one `UiState` machine → consistent Loading/Empty/Error/Offline.

## 6. DATABASE SCHEMA (designed; implemented Phase 2 — Supabase)

See `docs/DATABASE_SCHEMA.md`. Summary of core tables:

`users, profiles, roles, user_roles, agencies, agency_members, permissions,`  
`properties, property_addresses, property_media, property_features, listings,`  
`listing_status_history, property_verifications, property_price_history, favorites,`  
`saved_searches, search_events, buyer_requirements, matches, leads,`  
`conversations, messages, visits, offers, notifications, reviews, reports, audit_logs`

Principles: FK + indexes + unique constraints + timestamps + soft-delete where needed; RLS on every table; verification writes append to `audit_logs`.

## 7. DEPENDENCY PLAN (Phase 1 — locked in `gradle/libs.versions.toml`)

| Area | Choice | Version | Why |
|------|--------|---------|-----|
| AGP | `com.android.application` | 8.7.3 | Stable with Gradle 8.11 |
| Kotlin | + compose + serialization plugins | 2.1.10 | Official multiplugin alignment |
| KSP | for Hilt | 2.1.10-1.0.30 | Faster than kapt |
| Compose | BOM | 2024.12.01 | Single version surface |
| DI | Hilt | 2.53.1 | Standard, KSP-ready |
| HTTP | Retrofit 2 + OkHttp 4 + kotlinx converter | 2.11.0 / 4.12.0 | Typed API + JSON |
| Images | Coil | 2.7.0 | Compose-native, lazy |
| Nav | Navigation Compose | 2.8.5 | Type-safe enough for Phase 1 |
| Font | Vazirmatn variable (bundled OFL) | google/fonts | Persian-first, offline |
| Static analysis | Detekt | 1.23.7 | CI gate |

**Explicitly deferred (per master prompt §47):** Room, Maps SDK, Paging, Supabase client, payment, 3D, ML — architecture leaves seams (`core:network`, repositories) for them.

## 8. IMPLEMENTATION ROADMAP

| Phase | Scope | Status after this session |
|-------|-------|---------------------------|
| **0 Audit** | Repo audit, architecture, schema, deps, roadmap | ✅ **Done** (this doc) |
| **1 Foundation** | Structure, design system, nav, theme, RTL, i18n, network, errors, DI, env | 🔄 **In progress — this session** |
| 2 Backend | Supabase schema, RLS, auth OTP, storage, seed, audit logs | ⬜ |
| 3 Marketplace | Home feed, search, filters, map, detail, favorites, saved search | ⬜ |
| 4 Seller | Add property wizard, media, publish/pause/sold | ⬜ |
| 5 Agent | Profile, dashboard, files, leads, matching, visits | ⬜ |
| 6 Communication | Chat, notifications, visit scheduling | ⬜ |
| 7 Trust | Verification, reports, moderation, freshness | ⬜ |
| 8 AI | NL search, compare, assistant (tool-calling, grounded) | ⬜ |

## 9. CRITICAL ISSUES REGISTER

| ID | Severity | Issue | Status |
|----|----------|-------|--------|
| C-01 | High | Zero prior code — full greenfield risk of accidental prototype traps | Mitigated: DataSource enum + unimplemented-section flags in Phase 1 |
| C-02 | High | Sandbox cannot reach Maven/Google — local `./gradlew` will fail here | Mitigated: CI-first build (D-001); `gradlew` wrapper committed for CI/dev machines |
| C-03 | Medium | No backend yet — any listing UI could drift into fake data | Mitigated: Home rails marked `isImplemented=false` → "به‌زودی" |
| C-04 | Medium | Auth not yet real | Mitigated: Profile shows honest "coming Phase 2" state; no mock OTP |
| C-05 | Low | Map tab is placeholder | Mitigated: explicit placeholder copy, not a static fake map image |

## 10. DEFINITION OF DONE — Phase 1 checklist

- [x] Multi-module structure matching target architecture
- [x] Design system: brand tokens, M3 theme, light/dark, Vazirmatn RTL typography
- [x] RTL forced at root (`LayoutDirection.Rtl`)
- [x] Localization: `values` (fa) + `values-en` skeleton, key parity
- [x] Networking: Retrofit + OkHttp + kotlinx.serialization + unified `AppError`
- [x] Error model: Offline/Server/Client/Unauthorized/Validation/… + `UiStateRenderer`
- [x] DI: Hilt app + network + analytics modules
- [x] Environment: `AppConfig` with PRODUCTION/STAGING/DEVELOPMENT + prod guard rails
- [x] Analytics: typed event schema (names from master prompt §39)
- [x] Navigation: customer tabs, role-ready structure
- [x] Tests: Format (Persian digits/price), AppResult/UiState, ErrorMapper, HomeViewModel, Analytics schema
- [x] CI: test → detekt → lint → assembleDebug (+ release gate on main)
- [x] **CI green on `arena/01a0d112-4divari`** — run `35948318038` (commit `abffaa6`): all steps success (tests, detekt, lint, assembleDebug)

---

## 11. Post-Phase-1 CI notes (this branch)

| Run | Result | Lesson |
|-----|--------|--------|
| probe | ✅ | Actions available on public repo |
| first foundation push | ❌ | root Detekt FQN broke all Gradle tasks |
| OFL license in `res/font` | ❌ | Android rejects non-font files in `res/font` |
| compile rounds | ❌→✅ | Composable context, Int/Long, suspend type, Failure generics, retrofit package |
| `35948318038` @ `abffaa6` | ✅ **GREEN** | test + detekt + lint + assembleDebug all pass |
| `35950252454` @ `e456ecd` | ✅ **GREEN** | confirms detekt full-tree source config + docs update |

*No fake polish. Every placeholder above is labeled as placeholder.*
