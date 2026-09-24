package ir.chardivari.core.marketplace

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.chardivari.core.environment.AppConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketplaceBindModule {
    @Binds
    @Singleton
    abstract fun bindListingRepository(
        impl: SupabaseListingRepository,
    ): ListingRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: SupabaseFavoritesRepository,
    ): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindSavedSearchRepository(
        impl: SupabaseSavedSearchRepository,
    ): SavedSearchRepository

    @Binds
    @Singleton
    abstract fun bindListingContactRepository(
        impl: SupabaseListingContactRepository,
    ): ListingContactRepository

    @Binds
    @Singleton
    abstract fun bindSellerRepository(
        impl: SupabaseSellerRepository,
    ): SellerRepository
}

/**
 * PostgREST lives at {supabaseUrl}/rest/v1/ — not AppConfig.apiBaseUrl.
 * Reuses the app OkHttpClient (Bearer interceptor + logging policy).
 */
@Module
@InstallIn(SingletonComponent::class)
object MarketplaceModule {

    @Provides
    @Singleton
    fun provideMarketplaceApi(
        config: AppConfig,
        client: OkHttpClient,
        json: Json,
    ): MarketplaceApi {
        val raw = config.supabaseUrl?.trimEnd('/')
        val base = if (raw.isNullOrEmpty()) {
            "https://rest.not-configured.invalid/"
        } else {
            "$raw/rest/v1/"
        }
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MarketplaceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideStorageBaseUrl(config: AppConfig): StorageBaseUrl =
        StorageBaseUrl(config.supabaseUrl?.trimEnd('/'))
}

/** Public storage origin for property-media URLs (null when unconfigured). */
data class StorageBaseUrl(val value: String?)
