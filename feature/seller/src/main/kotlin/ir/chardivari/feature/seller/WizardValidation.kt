package ir.chardivari.feature.seller

import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.buildSellerListingPayload
import ir.chardivari.core.marketplace.buildSellerPropertyPayload
import kotlinx.serialization.json.JsonObject

/**
 * Pure wizard validation — unit-tested, no Android deps.
 * Returns a Persian message when invalid, null when the step can advance.
 */
internal fun WizardDraft.validateStep(step: WizardStep): String? = when (step) {
    WizardStep.TYPE -> when {
        dealType == null -> "نوع معامله را انتخاب کنید"
        propertyType == null -> "نوع ملک را انتخاب کنید"
        else -> null
    }

    WizardStep.LOCATION -> when {
        province.isBlank() -> "استان را وارد کنید"
        city.isBlank() -> "شهر را وارد کنید"
        else -> null
    }

    WizardStep.SPECS -> {
        val area = areaSqm.trim().toIntOrNull()
        when {
            area == null || area <= 0 -> "متراژ را به عدد وارد کنید"
            bedrooms.trim().isNotEmpty() && bedrooms.trim().toIntOrNull() == null ->
                "تعداد خواب باید عدد باشد"
            floor.trim().isNotEmpty() && (floor.trim().toIntOrNull() == null || floor.trim().toInt() < 0) ->
                "طبقه باید عدد باشد"
            totalFloors.trim().isNotEmpty() &&
                (totalFloors.trim().toIntOrNull() == null || totalFloors.trim().toInt() <= 0) ->
                "تعداد طبقات باید عدد باشد"
            buildYear.trim().isNotEmpty() -> {
                val year = buildYear.trim().toIntOrNull()
                if (year == null || year !in 1100..1500) {
                    "سال ساخت (شمسی) بین ۱۱۰۰ تا ۱۵۰۰ است"
                } else {
                    null
                }
            }
            else -> null
        }
    }

    WizardStep.AMENITIES -> null

    WizardStep.PRICE -> when (dealType) {
        DealType.SALE -> if (priceRial.trim().toLongOrNull()?.let { it > 0 } != true) {
            "قیمت فروش را به عدد وارد کنید"
        } else {
            null
        }
        DealType.RENT -> if (rentRial.trim().toLongOrNull()?.let { it > 0 } != true) {
            "اجاره ماهانه را به عدد وارد کنید"
        } else {
            null
        }
        DealType.RENT_WITH_DEPOSIT -> when {
            depositRial.trim().toLongOrNull()?.let { it > 0 } != true -> "مبلغ رهن را وارد کنید"
            rentRial.trim().toLongOrNull()?.let { it > 0 } != true -> "اجاره ماهانه را وارد کنید"
            else -> null
        }
        null -> "ابتدا نوع معامله را انتخاب کنید"
    }

    WizardStep.PHOTOS ->
        if (photos.isEmpty()) "حداقل یک تصویر اضافه کنید" else null

    WizardStep.DOCS -> null

    WizardStep.REVIEW -> null
}

/** First step (from the beginning) whose validation fails — publish gate. */
internal fun WizardDraft.firstInvalidStep(): WizardStep? =
    WizardStep.entries.firstOrNull { validateStep(it) != null }

/** First failing step strictly before [target] — forward-jump gate. */
internal fun WizardDraft.firstInvalidStepBefore(target: WizardStep): WizardStep? =
    WizardStep.entries
        .takeWhile { it.ordinal < target.ordinal }
        .firstOrNull { validateStep(it) != null }

/** jsonb payload for `seller_create_draft.p_property` (validated by publish()). */
internal fun WizardDraft.propertyPayload(): JsonObject {
    val area = areaSqm.trim().toInt()
    return buildSellerPropertyPayload(
        propertyType = propertyType?.name ?: error("propertyType required"),
        areaSqm = area,
        bedrooms = bedrooms.trim().toIntOrNull() ?: 0,
        province = province.trim(),
        city = city.trim(),
        neighborhood = neighborhood.trim(),
        floor = floor.trim().toIntOrNull(),
        totalFloors = totalFloors.trim().toIntOrNull(),
        buildYear = buildYear.trim().toIntOrNull(),
        hasElevator = hasElevator,
        hasParking = hasParking,
        hasStorage = hasStorage,
        hasBalcony = hasBalcony,
        description = description.trim(),
    )
}

/** jsonb payload for `seller_create_draft.p_listing` (validated by publish()). */
internal fun WizardDraft.listingPayload(): JsonObject {
    val price = priceRial.trim().toLongOrNull()
    val deposit = depositRial.trim().toLongOrNull()
    val rent = rentRial.trim().toLongOrNull()
    return when (dealType) {
        DealType.SALE -> buildSellerListingPayload(
            dealType = DealType.SALE.name,
            priceRial = requireNotNull(price) { "price required" },
            depositRial = null,
            rentRial = null,
            terms = null,
        )
        DealType.RENT -> buildSellerListingPayload(
            dealType = DealType.RENT.name,
            priceRial = requireNotNull(rent) { "rent required" },
            depositRial = null,
            rentRial = rent,
            terms = null,
        )
        DealType.RENT_WITH_DEPOSIT -> buildSellerListingPayload(
            dealType = DealType.RENT_WITH_DEPOSIT.name,
            priceRial = requireNotNull(deposit) { "deposit required" },
            depositRial = deposit,
            rentRial = rent,
            terms = null,
        )
        null -> error("dealType required")
    }
}
