package ir.chardivari.core.auth

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.SessionTokenProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindModule {
    @Binds
    @Singleton
    abstract fun bindSessionStore(impl: DataStoreSessionStore): SessionStore

    @Binds
    @Singleton
    abstract fun bindSessionTokenProvider(
        impl: AuthSessionTokenProvider,
    ): SessionTokenProvider

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository
}

/**
 * GoTrue lives at {supabaseUrl}/auth/v1/, not AppConfig.apiBaseUrl.
 * SessionTokenProvider depends only on SessionStore — no Hilt cycle with Retrofit.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthApi(config: AppConfig, client: OkHttpClient, json: Json): AuthApi {
        val raw = config.supabaseUrl?.trimEnd('/')
        val base = if (raw.isNullOrEmpty()) {
            "https://auth.not-configured.invalid/"
        } else {
            "$raw/"
        }
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
    }
}
