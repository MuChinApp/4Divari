package ir.chardivari.core.environment

/**
 * Single source of truth for environment identity.
 *
 * Anti-prototype rule: every piece of data shown in UI is labeled with its
 * [DataSource]. Development fixtures are NEVER shaped to be confused with
 * production responses — they carry a visible label and fail closed when
 * [AppEnvironment.isProduction] is true.
 */
enum class AppEnvironment {
    /** Real backend, real auth, real persistence. */
    PRODUCTION,

    /** Staging/preview backend. */
    STAGING,

    /**
     * Local development fixtures only.
     * Must be impossible to reach from release builds.
     */
    DEVELOPMENT;

    val isProduction: Boolean get() = this == PRODUCTION
    val isDevelopment: Boolean get() = this == DEVELOPMENT
}

/**
 * Origin of a value rendered on screen.
 * Surface this in dev overlays; strip from release UI copy but keep in analytics.
 */
enum class DataSource {
    /** Fetched from live API/DB. */
    REAL,

    /** Seeded fixture for local/dev backend — labeled in UI. */
    DEV_FIXTURE,

    /** Not yet wired — UI must show "coming soon"/disabled, not fake content. */
    PLACEHOLDER,
}

/** Runtime configuration injected via Hilt from BuildConfig. */
data class AppConfig(
    val environment: AppEnvironment,
    val apiBaseUrl: String,
    val supabaseUrl: String?,
    val supabaseAnonKey: String?,
    val enableNetworkLogging: Boolean,
    val enableDeveloperOverlay: Boolean,
) {
    init {
        require(
            !(environment == AppEnvironment.PRODUCTION && enableNetworkLogging),
        ) { "Network logging must be disabled in production" }
        require(
            !(environment == AppEnvironment.PRODUCTION && enableDeveloperOverlay),
        ) { "Developer overlay must be disabled in production" }
    }

    companion object {
        fun fromBuildConfig(
            environmentName: String,
            baseUrl: String,
            supabaseUrl: String,
            supabaseAnonKey: String,
            debug: Boolean,
        ): AppConfig {
            val env = runCatching {
                AppEnvironment.valueOf(environmentName)
            }.getOrDefault(
                if (debug) AppEnvironment.DEVELOPMENT else AppEnvironment.PRODUCTION,
            )

            return AppConfig(
                environment = env,
                apiBaseUrl = baseUrl.trimEnd('/'),
                supabaseUrl = supabaseUrl.ifBlank { null },
                supabaseAnonKey = supabaseAnonKey.ifBlank { null },
                enableNetworkLogging = debug && env != AppEnvironment.PRODUCTION,
                enableDeveloperOverlay = debug,
            )
        }
    }
}
