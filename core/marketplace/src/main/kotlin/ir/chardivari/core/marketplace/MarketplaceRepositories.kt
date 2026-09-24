package ir.chardivari.core.marketplace

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.SafeApi
import ir.chardivari.core.network.SessionTokenProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Read/write ACTIVE listings + search. */
interface ListingRepository {
    suspend fun feed(limit: Int = 20): AppResult<List<Listing>>

    suspend fun search(
        filters: SearchFilters,
        limit: Int = 30,
    ): AppResult<List<Listing>>

    suspend fun byId(listingId: String): AppResult<Listing>
}

/** Owner-scoped favorites (requires authenticated session). */
interface FavoritesRepository {
    suspend fun favoriteIds(): AppResult<Set<String>>

    suspend fun setFavorite(listingId: String, favorite: Boolean): AppResult<Unit>
}

/** Owner-scoped saved searches (requires authenticated session). */
interface SavedSearchRepository {
    suspend fun list(): AppResult<List<SavedSearchItem>>

    suspend fun save(
        name: String?,
        filters: SearchFilters,
    ): AppResult<SavedSearchItem>

    suspend fun delete(id: String): AppResult<Unit>
}

/** Contact channel for a listing — SECURITY DEFINER RPC, not raw users table. */
interface ListingContactRepository {
    suspend fun contact(listingId: String): AppResult<ListingContact>
}

data class SavedSearchItem(
    val id: String,
    val name: String?,
    val filters: SearchFilters,
    val createdAt: String?,
)

data class ListingContact(
    val phoneE164: String,
    val displayName: String?,
    val partyRole: String?,
)

/** Shared config probe — missing Supabase fails closed (never invents data). */
internal fun AppConfig.requireRest(): Pair<String, String>? {
    val base = supabaseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
    val key = supabaseAnonKey?.takeIf { it.isNotBlank() } ?: return null
    return base to key
}

internal fun marketplaceConfigError(): AppResult.Failure = AppResult.Failure(
    AppError.Client(
        code = 0,
        serverMessage = "Backend marketplace is not configured for this build",
    ),
)

/**
 * Supabase PostgREST-backed repositories.
 *
 * Prototype Trap: missing SUPABASE_URL / anon key fails closed with
 * AppError.Client — never invents empty success for a misconfigured build.
 * Public ACTIVE reads send only `apikey`; session Bearer is added by
 * core:network when present.
 */
@Singleton
class SupabaseListingRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
) : ListingRepository {

    override suspend fun feed(limit: Int): AppResult<List<Listing>> =
        search(SearchFilters.EMPTY, limit)

    override suspend fun search(
        filters: SearchFilters,
        limit: Int,
    ): AppResult<List<Listing>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val query = ListingQuery.build(filters, limit = limit)
        return when (val result = safeApi.call {
            api.fetchListings(url = "$base/rest/v1/listings?$query", apiKey = key, accept = "application/json")
        }) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty()
                if (rows.isEmpty()) {
                    AppResult.Empty
                } else {
                    AppResult.Success(rows.map { it.toDomain() })
                }
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun byId(listingId: String): AppResult<Listing> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val query =
            "select=${ListingQuery.SELECT}&id=eq.$listingId&status=eq.ACTIVE&limit=1"
        return when (val result = safeApi.call {
            api.fetchListingById(url = "$base/rest/v1/listings?$query", apiKey = key, accept = "application/json")
        }) {
            is AppResult.Success -> {
                val row = result.data?.firstOrNull()
                if (row == null) {
                    AppResult.Empty
                } else {
                    AppResult.Success(row.toDomain())
                }
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }
}

@Singleton
class SupabaseFavoritesRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : FavoritesRepository {

    override suspend fun favoriteIds(): AppResult<Set<String>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val userId = session.currentUserId()
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (val result = safeApi.call {
            api.fetchFavorites(
                url = "$base/rest/v1/favorites",
                apiKey = key,
                select = "listing_id,created_at",
                userId = userId,
                order = "created_at.desc",
            )
        }) {
            is AppResult.Success -> {
                AppResult.Success(result.data.orEmpty().map { it.listingId }.toSet())
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Success(emptySet())
        }
    }

    override suspend fun setFavorite(
        listingId: String,
        favorite: Boolean,
    ): AppResult<Unit> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val userId = session.currentUserId()
            ?: return AppResult.Failure(AppError.Unauthorized)
        val token = session.accessToken()
            ?: return AppResult.Failure(AppError.Unauthorized)
        val authHeader = "Bearer $token"

        return if (favorite) {
            val body = FavoriteWriteDto(listingId = listingId, userId = userId)
            when (val result = safeApi.call {
                api.insertFavorite(
                    apiKey = key,
                    authorization = authHeader,
                    body = body,
                )
            }) {
                is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
                is AppResult.Failure -> {
                    // 409 = already favorited — idempotent success.
                    val err = result.error
                    if (err is AppError.Client && err.code == 409) {
                        AppResult.Success(Unit)
                    } else {
                        result
                    }
                }
            }
        } else {
            val url = "$base/rest/v1/favorites?" +
                "user_id=eq.$userId&listing_id=eq.$listingId"
            when (val result = safeApi.call {
                api.deleteFavorite(url = url, apiKey = key, authorization = authHeader)
            }) {
                is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
                is AppResult.Failure -> result
            }
        }
    }
}

@Singleton
class SupabaseSavedSearchRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
    private val json: Json,
) : SavedSearchRepository {

    override suspend fun list(): AppResult<List<SavedSearchItem>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val userId = session.currentUserId()
            ?: return AppResult.Failure(AppError.Unauthorized)
        val authHeader = authHeaderOrNull() ?: return AppResult.Failure(AppError.Unauthorized)

        return when (val result = safeApi.call {
            api.fetchSavedSearches(
                url = "$base/rest/v1/saved_searches",
                apiKey = key,
                authorization = authHeader,
                select = "id,name,query,notify_new_listing,notify_price_drop,created_at",
                userId = userId,
                order = "created_at.desc",
            )
        }) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().mapNotNull { it.toItem(json) }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun save(
        name: String?,
        filters: SearchFilters,
    ): AppResult<SavedSearchItem> {
        config.requireRest() ?: return marketplaceConfigError()
        val userId = session.currentUserId()
            ?: return AppResult.Failure(AppError.Unauthorized)
        val authHeader = authHeaderOrNull() ?: return AppResult.Failure(AppError.Unauthorized)
        val queryJson = runCatching {
            json.encodeToJsonElement(SearchFilters.serializer(), filters)
        }.getOrNull() as? JsonObject
            ?: return AppResult.Failure(AppError.Serialization)

        val body = SavedSearchWriteDto(
            name = name?.takeIf { it.isNotBlank() }?.trim()?.take(120),
            query = queryJson,
            userId = userId,
        )
        return when (val result = safeApi.call {
            api.insertSavedSearch(
                apiKey = config.supabaseAnonKey.orEmpty(),
                authorization = authHeader,
                body = body,
            )
        }) {
            is AppResult.Success -> {
                val row = result.data
                if (row == null) {
                    AppResult.Empty
                } else {
                    AppResult.Success(
                        row.toItem(json) ?: SavedSearchItem(
                            id = "",
                            name = body.name,
                            filters = filters,
                            createdAt = null,
                        ),
                    )
                }
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val authHeader = authHeaderOrNull() ?: return AppResult.Failure(AppError.Unauthorized)
        return when (val result = safeApi.call {
            api.deleteSavedSearch(
                url = "$base/rest/v1/saved_searches?id=eq.$id",
                apiKey = key,
                authorization = authHeader,
            )
        }) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    private fun authHeaderOrNull(): String? =
        session.accessToken()?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }
}

private fun SavedSearchDto.toItem(json: Json): SavedSearchItem? {
    if (id.isBlank()) return null
    val filters = query?.let { obj ->
        runCatching {
            json.decodeFromJsonElement(SearchFilters.serializer(), obj)
        }.getOrNull()
    } ?: SearchFilters.EMPTY
    return SavedSearchItem(
        id = id,
        name = name,
        filters = filters,
        createdAt = createdAt,
    )
}

@Singleton
class SupabaseListingContactRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
) : ListingContactRepository {

    override suspend fun contact(listingId: String): AppResult<ListingContact> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        return when (val result = safeApi.call {
            api.listingContact(
                apiKey = key,
                body = ListingContactRequestDto(listingId = listingId),
            )
        }) {
            is AppResult.Success -> {
                val row = result.data?.firstOrNull { !it.phoneE164.isNullOrBlank() }
                val phone = row?.phoneE164
                if (phone.isNullOrBlank()) {
                    AppResult.Empty
                } else {
                    AppResult.Success(
                        ListingContact(
                            phoneE164 = phone,
                            displayName = row.displayName,
                            partyRole = row.partyRole,
                        ),
                    )
                }
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }
}
