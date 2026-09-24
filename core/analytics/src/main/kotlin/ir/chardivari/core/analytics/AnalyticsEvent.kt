package ir.chardivari.core.analytics

/**
 * Analytics event contract — privacy-aware, no PII in event names/params.
 *
 * Schema-first: adding an event means adding a sealed subtype + test.
 * Null params are dropped before transport.
 */
sealed interface AnalyticsEvent {
    val name: String
    val params: Map<String, String>
        get() = emptyMap()

    data object PropertyView : AnalyticsEvent {
        override val name: String = "property_view"
    }

    data class PropertyFavorite(val propertyId: String, val favorited: Boolean) : AnalyticsEvent {
        override val name: String = "property_favorite"
        override val params: Map<String, String> = mapOf(
            "property_id" to propertyId,
            "favorited" to favorited.toString(),
        )
    }

    data class PropertyShare(val propertyId: String) : AnalyticsEvent {
        override val name: String = "property_share"
        override val params: Map<String, String> = mapOf("property_id" to propertyId)
    }

    data class SearchCreated(val queryLength: Int, val filterCount: Int) : AnalyticsEvent {
        override val name: String = "search_created"
        override val params: Map<String, String> = mapOf(
            "query_length" to queryLength.toString(),
            "filter_count" to filterCount.toString(),
        )
    }

    data class SearchSaved(val savedSearchId: String) : AnalyticsEvent {
        override val name: String = "search_saved"
        override val params: Map<String, String> = mapOf("saved_search_id" to savedSearchId)
    }

    data class FilterUsed(val filterKey: String) : AnalyticsEvent {
        override val name: String = "filter_used"
        override val params: Map<String, String> = mapOf("filter" to filterKey)
    }

    data class MapInteraction(val kind: String) : AnalyticsEvent {
        override val name: String = "map_interaction"
        override val params: Map<String, String> = mapOf("kind" to kind)
    }

    data class ContactAgent(val propertyId: String, val channel: String) : AnalyticsEvent {
        override val name: String = "contact_agent"
        override val params: Map<String, String> = mapOf(
            "property_id" to propertyId,
            "channel" to channel,
        )
    }

    data class VisitRequested(val propertyId: String) : AnalyticsEvent {
        override val name: String = "visit_requested"
        override val params: Map<String, String> = mapOf("property_id" to propertyId)
    }

    data class VisitCompleted(val visitId: String) : AnalyticsEvent {
        override val name: String = "visit_completed"
        override val params: Map<String, String> = mapOf("visit_id" to visitId)
    }

    data class OfferCreated(val offerId: String) : AnalyticsEvent {
        override val name: String = "offer_created"
        override val params: Map<String, String> = mapOf("offer_id" to offerId)
    }

    data class ListingCreated(val listingId: String) : AnalyticsEvent {
        override val name: String = "listing_created"
        override val params: Map<String, String> = mapOf("listing_id" to listingId)
    }

    data class ListingVerified(val listingId: String) : AnalyticsEvent {
        override val name: String = "listing_verified"
        override val params: Map<String, String> = mapOf("listing_id" to listingId)
    }

    data class LeadCreated(val leadId: String, val source: String) : AnalyticsEvent {
        override val name: String = "lead_created"
        override val params: Map<String, String> = mapOf(
            "lead_id" to leadId,
            "source" to source,
        )
    }

    data class ScreenView(val screen: String) : AnalyticsEvent {
        override val name: String = "screen_view"
        override val params: Map<String, String> = mapOf("screen" to screen)
    }

    data object AuthOtpRequested : AnalyticsEvent {
        override val name: String = "auth_otp_requested"
    }

    data object AuthLoginSucceeded : AnalyticsEvent {
        override val name: String = "auth_login_succeeded"
    }

    data class AuthLoginFailed(val reason: String) : AnalyticsEvent {
        override val name: String = "auth_login_failed"
        override val params: Map<String, String> = mapOf("reason" to reason)
    }
}
