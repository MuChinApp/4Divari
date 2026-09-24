package ir.chardivari.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.chardivari.core.environment.AppConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(config: AppConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (config.enableNetworkLogging) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                },
            )
        }

        // Authorization interceptor is added in Phase 2 (auth feature).
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(config: AppConfig, client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(config.apiBaseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith('/')) this else "$this/"
}
