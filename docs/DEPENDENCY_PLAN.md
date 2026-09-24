# Dependency Plan

Single source of truth: `gradle/libs.versions.toml`.

## Phase 1 (implemented)

| Coordinate | Version | Scope |
|------------|---------|-------|
| AGP | 8.7.3 | build |
| Kotlin (+ compose, serialization plugins) | 2.1.10 | build |
| KSP | 2.1.10-1.0.30 | Hilt codegen |
| Gradle wrapper | 8.11.1 | build |
| compose-bom | 2024.12.01 | UI |
| material3, ui, icons-extended | BOM | UI |
| androidx.core:core-ktx | 1.15.0 | AndroidX |
| activity-compose | 1.9.3 | AndroidX |
| lifecycle-runtime/viewmodel-compose | 2.8.7 | AndroidX |
| navigation-compose | 2.8.5 | Nav |
| hilt / hilt-android-compiler | 2.53.1 | DI |
| hilt-navigation-compose | 1.2.0 | DI |
| retrofit + converter-kotlinx-serialization | 2.11.0 | Network |
| okhttp + logging-interceptor | 4.12.0 | Network |
| kotlinx-serialization-json | 1.7.3 | JSON |
| kotlinx-coroutines(-android/test) | 1.10.1 | Async |
| coil-compose | 2.7.0 | Images |
| datastore-preferences | 1.1.1 | (declared for Phase 2 prefs) |
| junit / truth / turbine / mockk | current | Tests |
| detekt | 1.23.7 | Static analysis |

## Explicitly NOT in Phase 1 (and why)

| Missing on purpose | Adds in |
|--------------------|---------|
| Room | Phase 3 offline favorites cache |
| Play Services Maps / MapLibre | Phase 3 map (provider abstraction first) |
| supabase-kt | Phase 2 after schema+RLS verified |
| Paging3 | Phase 3 when feed endpoints exist |
| Coil video / 3D | Later media phases |
| AI/LLM SDKs | Phase 8 behind tool-calling orchestrator |

## Version policy

- Pin everything in the catalog; dependabot/renovate optional later.  
- Upgrades only with CI green + release notes skimmed.  
- No jcenter / no legacy `androidx.databinding` unless required.
