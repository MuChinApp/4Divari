package ir.chardivari.core.auth

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.chardivari.core.analytics.AnalyticsTracker

/**
 * Runtime access to the Hilt graph from code that does not run KSP
 * (feature:auth avoids KSP to prevent a known AGP task cycle:
 * kspDebugKotlin ↔ bundleLibCompileToJarDebug).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthEntryPoint {
    fun authRepository(): AuthRepository
    fun analyticsTracker(): AnalyticsTracker
}
