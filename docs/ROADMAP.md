# 4Divari — Implementation Roadmap

Aligned with master prompt §48. Each phase ends with CI green + Definition of Done review (§51).

| Phase | Goal | Key deliverables | Depends on |
|-------|------|------------------|------------|
| **0** Audit | Understand ground truth | This report, ADRs, schema, deps | — |
| **1** Foundation | Skeleton that can't lie | Modules, design system, RTL, nav, network, errors, DI, env, CI | 0 |
| **2** Backend | Real persistence + auth | Supabase project, SQL migrations (DATABASE_SCHEMA.md), RLS, phone OTP, storage buckets, seed, audit logs, verify via integration tests | 1 |
| **3** Marketplace | Customer core loop | Home real feed, search + filters, map, property detail, favorites, saved search, share, contact | 2 |
| **4** Seller | Listing lifecycle | Wizard (type→location→specs→amenities→price→photos→docs→review→publish), manage/pause/sold | 2–3 |
| **5** Agent | Professional tooling | Agent profile, dashboard, files, leads pipeline, buyer requirements, matching scores, visits | 2–4 |
| **6** Communication | Human connection | Chat (realtime), notifications, visit scheduling w/ double-book guard, contact flows | 2, 5 |
| **7** Trust | Reduce uncertainty | Verifications, reports, moderation queue, freshness nudge, fraud signals | 2–6 |
| **8** AI | Decision support | NL search → structured intent → real Search tool, compare explanations, price range w/ uncertainty, fairness constraints | 3, 7 (data must be real first) |

## Out of MVP (architecture-ready only)

Full online contract, payment settlement, advanced valuation AI, 3D tours, CV, commission marketplace, mortgages, AI agent network (master prompt §47).

## Quality gates every phase

- Unit tests for ViewModels, use cases, validators, matching, pricing  
- Error/empty/loading states on every screen  
- RTL + Persian formatting QA  
- Analytics events for the phase  
- Detekt + Android Lint clean in CI  
- No unlabeled mock data  
