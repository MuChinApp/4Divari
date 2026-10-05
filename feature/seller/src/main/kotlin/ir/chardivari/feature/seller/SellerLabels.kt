package ir.chardivari.feature.seller

import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.SellerAction

/** Persian labels — backend stays language-neutral. */
internal fun statusLabel(status: String): String = when (status) {
    "DRAFT" -> "پیش‌نویس"
    "PENDING_VERIFICATION" -> "در انتظار بررسی"
    "ACTIVE" -> "منتشرشده"
    "PAUSED" -> "متوقف"
    "UNDER_OFFER" -> "در معامله"
    "SOLD" -> "فروخته‌شده"
    "RENTED" -> "اجاره‌رفته"
    "EXPIRED" -> "منقضی"
    "REJECTED" -> "ردشده"
    else -> status
}

/** Lifecycle actions allowed for a status (mirrors RPC transition map). */
internal fun allowedActions(status: String): List<SellerAction> = when (status) {
    "DRAFT", "PENDING_VERIFICATION" -> listOf(SellerAction.PUBLISH)
    "ACTIVE" -> listOf(SellerAction.PAUSE, SellerAction.MARK_SOLD)
    "PAUSED" -> listOf(SellerAction.RESUME, SellerAction.MARK_SOLD)
    "UNDER_OFFER" -> listOf(SellerAction.MARK_SOLD)
    else -> emptyList()
}

internal fun actionLabel(action: SellerAction): String = when (action) {
    SellerAction.PUBLISH -> "انتشار"
    SellerAction.PAUSE -> "توقف"
    SellerAction.RESUME -> "انتشار مجدد"
    SellerAction.MARK_SOLD -> "فروخته شد"
}

internal fun dealLabel(dealType: DealType): String = when (dealType) {
    DealType.SALE -> "فروش"
    DealType.RENT -> "اجاره"
    DealType.RENT_WITH_DEPOSIT -> "رهن و اجاره"
}

internal fun propertyTypeLabel(type: ir.chardivari.core.marketplace.PropertyType): String =
    when (type) {
        ir.chardivari.core.marketplace.PropertyType.APARTMENT -> "آپارتمان"
        ir.chardivari.core.marketplace.PropertyType.HOUSE -> "خانه"
        ir.chardivari.core.marketplace.PropertyType.VILLA -> "ویلا"
        ir.chardivari.core.marketplace.PropertyType.LAND -> "زمین"
        ir.chardivari.core.marketplace.PropertyType.SHOP -> "مغازه"
        ir.chardivari.core.marketplace.PropertyType.OFFICE -> "دفتر"
        ir.chardivari.core.marketplace.PropertyType.INDUSTRIAL -> "صنعتی"
        ir.chardivari.core.marketplace.PropertyType.OTHER -> "سایر"
    }
