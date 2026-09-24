package ir.chardivari.core.marketplace

import ir.chardivari.core.common.Format
import ir.chardivari.core.common.toPersianDigits

/**
 * Presentation models for marketplace cards / detail — Persian formatting
 * happens here (edge), backend payloads stay language-neutral.
 */
data class ListingCardUi(
    val listingId: String,
    val photoUrl: String?,
    val priceLine: String,
    val pricePerSqmLine: String,
    val titleLine: String,
    val locationLine: String,
    val amenitiesLine: String?,
    val verificationLabel: String?,
    val updatedLabel: String,
    val isFixture: Boolean,
    val isFavorited: Boolean,
) {
    val shareText: String
        get() = buildString {
            append(titleLine)
            append(" — ")
            append(priceLine)
            append("\n")
            append(locationLine)
            append("\n")
            append("در چاردیواری ببینید")
        }
}

data class PropertyDetailUi(
    val listingId: String,
    val photoUrls: List<String>,
    val priceLine: String,
    val pricePerSqmLine: String,
    val depositLine: String?,
    val titleLine: String,
    val locationLine: String,
    val specsLines: List<String>,
    val description: String?,
    val verificationLabel: String?,
    val updatedLabel: String,
    val isFixture: Boolean,
    val isFavorited: Boolean,
    val shareText: String,
)

object ListingPresenter {

    fun card(
        listing: Listing,
        storageBaseUrl: String?,
        favorited: Boolean = false,
    ): ListingCardUi {
        val p = listing.property
        val priceLine = priceLine(listing)
        val title = propertyTypeLabel(p.propertyType) +
            " " + Format.area(p.areaSqm) +
            (if (p.bedrooms > 0) "، " + Format.bedrooms(p.bedrooms) else "")
        val location = listOfNotNull(p.city, p.neighborhood)
            .joinToString("، ")
        val amenities = buildList {
            if (p.hasParking) add("پارکینگ")
            if (p.hasElevator) add("آسانسور")
            if (p.hasStorage) add("انباری")
        }.takeIf { it.isNotEmpty() }?.joinToString(" • ")

        return ListingCardUi(
            listingId = listing.id,
            photoUrl = listing.coverImageUrl(storageBaseUrl),
            priceLine = priceLine,
            pricePerSqmLine = Format.pricePerSqm(listing.priceRial, p.areaSqm)
                ?: Format.area(p.areaSqm),
            titleLine = title,
            locationLine = location,
            amenitiesLine = amenities,
            verificationLabel = if (listing.isVerified) "تأییدشده" else null,
            updatedLabel = updatedLabel(listing),
            isFixture = listing.isFixture,
            isFavorited = favorited,
        )
    }

    fun detail(
        listing: Listing,
        storageBaseUrl: String?,
        favorited: Boolean = false,
    ): PropertyDetailUi {
        val p = listing.property
        val card = card(listing, storageBaseUrl, favorited)
        val photos = p.media
            .filter { it.mediaType == "image" }
            .sortedWith(compareBy({ !it.isCover }, { it.sortOrder }))
            .mapNotNull { media ->
                storageBaseUrl?.let { base ->
                    base.trimEnd('/') +
                        "/object/public/property-media/" +
                        media.storagePath.trimStart('/')
                }
            }

        val specs = buildList {
            add(Format.area(p.areaSqm) + " مساحت")
            if (p.builtAreaSqm != null) add("بنا " + Format.area(p.builtAreaSqm))
            add(Format.bedrooms(p.bedrooms))
            if (p.floor != null && p.totalFloors != null) {
                add(
                    "طبقه " + Format.toPersianDigits(p.floor) +
                        " از " + Format.toPersianDigits(p.totalFloors),
                )
            }
            if (p.buildYear != null) {
                add("ساخت " + Format.toPersianDigits(p.buildYear))
            }
            if (p.hasElevator) add("آسانسور")
            if (p.hasParking) add("پارکینگ")
            if (p.hasStorage) add("انباری")
            if (p.hasBalcony) add("بالکن")
            p.orientation?.let { add("جهت: $it") }
            p.renovationStatus?.let { add("نوسازی: $it") }
            p.deedStatus?.let { add("سند: $it") }
            p.province.takeIf { it.isNotBlank() }?.let { add("استان $it") }
        }

        return PropertyDetailUi(
            listingId = listing.id,
            photoUrls = photos,
            priceLine = card.priceLine,
            pricePerSqmLine = card.pricePerSqmLine,
            depositLine = listing.depositRial?.let { "ودیعه " + Format.price(it) },
            titleLine = card.titleLine,
            locationLine = card.locationLine,
            specsLines = specs,
            description = p.description?.takeIf { it.isNotBlank() },
            verificationLabel = card.verificationLabel,
            updatedLabel = card.updatedLabel,
            isFixture = listing.isFixture,
            isFavorited = favorited,
            shareText = card.shareText,
        )
    }

    fun priceLine(listing: Listing): String = when (listing.dealType) {
        DealType.SALE -> Format.price(listing.priceRial) + " تومان"
        DealType.RENT -> {
            val rent = listing.rentRial
            if (rent != null) {
                Format.price(rent) + " تومان اجاره"
            } else {
                Format.price(listing.priceRial) + " تومان"
            }
        }
        DealType.RENT_WITH_DEPOSIT -> {
            val deposit = listing.depositRial?.let { Format.price(it) } ?: "—"
            val rent = listing.rentRial?.let { Format.price(it) } ?: "—"
            "$deposit تومان ودیعه، $rent تومان اجاره"
        }
    }

    fun dealTypeLabel(dealType: DealType): String = when (dealType) {
        DealType.SALE -> "فروش"
        DealType.RENT -> "اجاره"
        DealType.RENT_WITH_DEPOSIT -> "رهن و اجاره"
    }

    fun propertyTypeLabel(type: PropertyType): String = when (type) {
        PropertyType.APARTMENT -> "آپارتمان"
        PropertyType.HOUSE -> "خانه"
        PropertyType.VILLA -> "ویلا"
        PropertyType.LAND -> "زمین"
        PropertyType.SHOP -> "مغازه"
        PropertyType.OFFICE -> "دفتر"
        PropertyType.INDUSTRIAL -> "صنعتی"
        PropertyType.OTHER -> "ملک"
    }

    private fun updatedLabel(listing: Listing): String {
        val published = listing.publishedAt ?: return "به‌روزرسانی نامشخص"
        // ISO-8601 date part only — Jalali conversion lands with locale module.
        val date = published.take(10)
        if (date.length < 10) return "به‌روزرسانی نامشخص"
        val parts = date.split("-")
        if (parts.size != 3) return "به‌روزرسانی نامشخص"
        val (y, m, d) = parts
        return "انتشار $y/$m/$d".let { it.toPersianDigits() }
    }
}
