package ir.chardivari.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.chardivari.app.BuildConfig
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.analytics.InMemoryAnalyticsTracker
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.environment.AppEnvironment
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig(
        environment = when (BuildConfig.APP_ENVIRONMENT) {
            "PRODUCTION" -> AppEnvironment.PRODUCTION
            "STAGING" -> AppEnvironment.STAGING
            else -> AppEnvironment.DEVELOPMENT
        },
        apiBaseUrl = BuildConfig.API_BASE_URL,
        supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { null },
        supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { null },
        enableNetworkLogging = BuildConfig.DEBUG,
        enableDeveloperOverlay = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    fun provideAnalytics(): AnalyticsTracker = InMemoryAnalyticsTracker()
}
