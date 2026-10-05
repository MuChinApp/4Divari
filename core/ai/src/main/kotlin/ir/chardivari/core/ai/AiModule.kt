package ir.chardivari.core.ai

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
abstract class AiBindModule {
    @Binds
    @Singleton
    abstract fun bindAiTransport(impl: SupabaseAiTransport): AiTransport

    @Binds
    @Singleton
    abstract fun bindAssistantEngine(impl: AssistantOrchestrator): AssistantEngine

    @Binds
    @Singleton
    abstract fun bindToolRegistry(impl: MarketplaceToolRegistry): AssistantToolRegistry
}

@Module
@InstallIn(SingletonComponent::class)
object AiProvideModule {

    @Provides
    @Singleton
    fun provideAiApi(
        config: AppConfig,
        client: OkHttpClient,
        json: Json,
    ): AiApi {
        val raw = config.supabaseUrl?.trimEnd('/')
        val base = if (raw.isNullOrEmpty()) {
            "https://functions.not-configured.invalid/"
        } else {
            "$raw/functions/v1/"
        }
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AiApi::class.java)
    }
}
