// Root build file — declare SDK levels once for all modules.
// Plugins are applied with `apply false`; each module opts in.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

// Shared Android SDK versions — single source of truth.
extra["compileSdk"] = 35
extra["minSdk"] = 24
extra["targetSdk"] = 35

allprojects {
    // Unique GAV/capability per module. Project names alone collide
    // (e.g. :core:auth and :feature:auth are both name "auth" with the
    // same group), which made feature:auth resolve a self-capability
    // and form a circular task graph on bundleLibCompileToJarDebug.
    val pathSuffix = project.path.trim(':').replace(':', '.')
    group = if (pathSuffix.isEmpty()) "ir.chardivari" else "ir.chardivari.$pathSuffix"
    version = "0.1.0"
}

// Root Detekt scans all Kotlin sources (main + test) across modules.
// Configured only via the `detekt {}` extension — no task type FQNs
// (those broke script compilation in an earlier CI iteration).
detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    // Phase 1 ramp-up: report debt but do not fail the pipeline.
    ignoreFailures = true
    source.setFrom(
        fileTree("app/src") { include("**/*.kt") },
        fileTree("core") { include("**/*.kt") },
        fileTree("feature") { include("**/*.kt") },
    )
}
