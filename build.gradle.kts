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
    group = "ir.chardivari"
    version = "0.1.0"
}

// Detekt static analysis — runs in CI, not required for local compile.
// Keep config minimal: avoid typing Detekt task class in Kotlin DSL
// (plugin classpath resolution differs across detekt releases).
detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = true
}

tasks.named("detekt") {
    // Default reports are fine; SARIF optional for code scanning later.
}
