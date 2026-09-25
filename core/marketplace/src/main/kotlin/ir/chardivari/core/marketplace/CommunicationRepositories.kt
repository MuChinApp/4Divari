package ir.chardivari.core.marketplace

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.SafeApi
import ir.chardivari.core.network.SessionTokenProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Phase 6 communication contracts — chat, notifications, buyer visits.
 *
 * Conversation entry + read-state writers are SECURITY DEFINER RPCs
 * (00095_communication_tooling.sql); messages/visits/notifications are plain
 * tables under their own RLS. Notification titles are language-neutral codes
 * mapped to Persian at the edge.
 */

@Serializable
data class StartConversationRequestDto(
    @SerialName("p_listing_id")
    val listingId: String,
)

@Serializable
data class MarkConversationReadRequestDto(
    @SerialName("p_conversation_id")
    val conversationId: String,
)

@Serializable
data class MarkNotificationReadRequestDto(
    @SerialName("p_notification_id")
    val notificationId: String,
)

@Serializable
data class LastMessageDto(
    val body: String? = null,
    @SerialName("sent_at")
    val sentAt: String? = null,
    @SerialName("sender_id")
    val senderId: String? = null,
)

/** One row of `my_conversations()` — server-computed unread + preview. */
@Serializable
data class ConversationRowDto(
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("counterpart_id")
    val counterpartId: String,
    @SerialName("unread_count")
    val unreadCount: Int = 0,
    @SerialName("last_message")
    val lastMessage: LastMessageDto? = null,
    @SerialName("listing_status")
    val listingStatus: String? = null,
    @SerialName("deal_type")
    val dealType: DealType? = null,
    @SerialName("price_rial")
    val priceRial: Long? = null,
    @SerialName("deposit_rial")
    val depositRial: Long? = null,
    @SerialName("rent_rial")
    val rentRial: Long? = null,
    val city: String? = null,
    val neighborhood: String? = null,
    @SerialName("area_sqm")
    val areaSqm: Int? = null,
)

/** Conversations list row. */
data class ConversationItem(
    val conversationId: String,
    val listingId: String,
    val counterpartId: String,
    val unreadCount: Int,
    val lastMessageBody: String?,
    val lastMessageAt: String?,
    val listingStatus: String?,
    val dealType: DealType?,
    val priceRial: Long?,
    val depositRial: Long?,
    val rentRial: Long?,
    val address: String?,
    val areaSqm: Int?,
)

fun ConversationRowDto.toConversationItem(): ConversationItem {
    val address = listOfNotNull(city, neighborhood)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("، ")
    return ConversationItem(
        conversationId = conversationId,
        listingId = listingId,
        counterpartId = counterpartId,
        unreadCount = unreadCount,
        lastMessageBody = lastMessage?.body,
        lastMessageAt = lastMessage?.sentAt,
        listingStatus = listingStatus,
        dealType = dealType,
        priceRial = priceRial,
        depositRial = depositRial,
        rentRial = rentRial,
        address = address,
        areaSqm = areaSqm,
    )
}

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("sender_id")
    val senderId: String,
    val body: String? = null,
    @SerialName("attachment_path")
    val attachmentPath: String? = null,
    @SerialName("sent_at")
    val sentAt: String,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
)

/** One chat message — [isMine] computed at the edge from the session. */
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String?,
    val sentAt: String,
    val isDeleted: Boolean,
    val isMine: Boolean,
)

fun MessageDto.toChatMessage(currentUserId: String?): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    body = if (deletedAt != null) null else body,
    sentAt = sentAt,
    isDeleted = deletedAt != null,
    isMine = currentUserId != null && senderId == currentUserId,
)

@Serializable
data class MessageWriteDto(
    @SerialName("conversation_id")
    val conversationId: String,
    @SerialName("sender_id")
    val senderId: String,
    val body: String,
)

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    /** Language-neutral code (e.g. `new_message`) — mapped to Persian in UI. */
    val title: String,
    val body: String? = null,
    val data: JsonObject? = null,
    @SerialName("read_at")
    val readAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)

data class AppNotification(
    val id: String,
    val type: String,
    val body: String?,
    val data: JsonObject?,
    val isRead: Boolean,
    val createdAt: String?,
)

/** Buyer-side visit row (agent side lives in AgentRepository). */
@Serializable
data class MyVisitDto(
    val id: String,
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("slot_start")
    val slotStart: String,
    @SerialName("slot_end")
    val slotEnd: String,
    val status: String,
    val listing: LeadListingRefDto? = null,
    val agent: LeadUserRefDto? = null,
)

data class MyVisit(
    val id: String,
    val listingId: String,
    val slotStart: String,
    val slotEnd: String,
    val status: String,
    val address: String?,
    val priceRial: Long?,
    val agentName: String?,
    val agentPhone: String?,
)

fun MyVisitDto.toMyVisit(): MyVisit {
    val property = listing?.property
    val address = listOfNotNull(property?.city, property?.neighborhood)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("، ")
    return MyVisit(
        id = id,
        listingId = listingId,
        slotStart = slotStart,
        slotEnd = slotEnd,
        status = status,
        address = address,
        priceRial = listing?.priceRial,
        agentName = agent?.profiles?.displayName,
        agentPhone = agent?.phoneE164,
    )
}

@Serializable
data class VisitWriteDto(
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("buyer_id")
    val buyerId: String,
    @SerialName("slot_start")
    val slotStart: String,
    @SerialName("slot_end")
    val slotEnd: String,
    val status: String = "REQUESTED",
)

@Serializable
data class VisitWriteRowDto(
    val id: String,
)

/** Chat: conversations + messages. */
interface ChatRepository {
    suspend fun conversations(): AppResult<List<ConversationItem>>

    /** Find-or-create the listing conversation; returns its id. */
    suspend fun startConversation(listingId: String): AppResult<String>

    suspend fun messages(conversationId: String): AppResult<List<ChatMessage>>

    suspend fun sendMessage(conversationId: String, body: String): AppResult<ChatMessage>

    suspend fun markRead(conversationId: String): AppResult<Unit>
}

/** In-app notifications (language-neutral codes, Persian at the edge). */
interface NotificationsRepository {
    suspend fun notifications(): AppResult<List<AppNotification>>

    suspend fun markRead(notificationId: String): AppResult<Unit>
}

/** Buyer-side visit scheduling (double-book guard lives in the DB index). */
interface VisitsRepository {
    suspend fun myVisits(): AppResult<List<MyVisit>>

    suspend fun requestVisit(
        listingId: String,
        slotStart: String,
        slotEnd: String,
    ): AppResult<Unit>

    suspend fun cancelVisit(visitId: String): AppResult<Unit>
}

private const val MY_VISITS_SELECT =
    "id,listing_id,slot_start,slot_end,status," +
        "listing:listings(id,deal_type,price_rial,deposit_rial,rent_rial,status," +
        "property:properties(property_type,area_sqm,city,neighborhood))," +
        "agent:users(id,phone_e164,profiles(display_name))"

@Singleton
class SupabaseChatRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : ChatRepository {

    override suspend fun conversations(): AppResult<List<ConversationItem>> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.myConversations(apiKey = key, authorization = "Bearer $auth")
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().map { it.toConversationItem() }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun startConversation(listingId: String): AppResult<String> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.startConversation(
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = StartConversationRequestDto(listingId = listingId),
                )
            }
        ) {
            is AppResult.Success -> {
                val id = result.data
                if (id.isNullOrBlank()) {
                    AppResult.Failure(AppError.Serialization)
                } else {
                    AppResult.Success(id)
                }
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun messages(conversationId: String): AppResult<List<ChatMessage>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchMessages(
                    url = "$base/rest/v1/messages",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    conversationId = "eq.$conversationId",
                    order = "sent_at.asc",
                )
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().map { it.toChatMessage(uid) }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        body: String,
    ): AppResult<ChatMessage> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed.length > 8000) {
            return AppResult.Failure(AppError.Validation())
        }
        return when (
            val result = safeApi.call {
                api.insertMessage(
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = MessageWriteDto(
                        conversationId = conversationId,
                        senderId = uid,
                        body = trimmed,
                    ),
                )
            }
        ) {
            is AppResult.Success -> {
                val row = result.data?.firstOrNull()
                    ?: return AppResult.Failure(AppError.Serialization)
                AppResult.Success(row.toChatMessage(uid))
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun markRead(conversationId: String): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.markConversationRead(
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = MarkConversationReadRequestDto(conversationId = conversationId),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }
}

@Singleton
class SupabaseNotificationsRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : NotificationsRepository {

    override suspend fun notifications(): AppResult<List<AppNotification>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchNotifications(
                    url = "$base/rest/v1/notifications",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = "id,type,title,body,data,read_at,created_at",
                    userId = "eq.$uid",
                    order = "created_at.desc",
                    limit = 100,
                )
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().map {
                    AppNotification(
                        id = it.id,
                        type = it.type,
                        body = it.body,
                        data = it.data,
                        isRead = it.readAt != null,
                        createdAt = it.createdAt,
                    )
                }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun markRead(notificationId: String): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.markNotificationRead(
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = MarkNotificationReadRequestDto(notificationId = notificationId),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }
}

@Singleton
class SupabaseVisitsRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : VisitsRepository {

    override suspend fun myVisits(): AppResult<List<MyVisit>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchMyVisits(
                    url = "$base/rest/v1/visits",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = MY_VISITS_SELECT,
                    buyerId = "eq.$uid",
                    order = "slot_start.asc",
                )
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().map { it.toMyVisit() }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun requestVisit(
        listingId: String,
        slotStart: String,
        slotEnd: String,
    ): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.insertVisit(
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = VisitWriteDto(
                        listingId = listingId,
                        buyerId = uid,
                        slotStart = slotStart,
                        slotEnd = slotEnd,
                    ),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    override suspend fun cancelVisit(visitId: String): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.updateMyVisit(
                    id = "eq.$visitId",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = VisitPatchDto(status = "CANCELLED"),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }
}
