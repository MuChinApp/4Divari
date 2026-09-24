package ir.chardivari.core.common

import java.util.Locale

/**
 * Locale-aware formatters for Persian UI.
 *
 * Backend contracts stay language-neutral (Latin digits, ISO-8601, IRR as Long);
 * display formatting happens only at the edge.
 */
object Format {

    private val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** Convert ASCII digits to Persian digits for display. */
    fun String.toPersianDigits(): String = buildString(length) {
        for (ch in this@toPersianDigits) {
            append(if (ch in '0'..'9') persianDigits[ch - '0'] else ch)
        }
    }

    fun toPersianDigits(value: Long): String = value.toString().toPersianDigits()

    /**
     * Group integer with thousands separators (Persian/ASCII), then Persian digits.
     * Example: 12_800_000_000 → «۱۲٬۸۰۰٬۰۰۰٬۰۰۰»
     */
    fun price(value: Long): String {
        val grouped = buildString {
            val s = value.toString()
            s.forEachIndexed { i, c ->
                if (i > 0 && (s.length - i) % 3 == 0) append('٬')
                append(c)
            }
        }
        return grouped.toPersianDigits()
    }

    /**
     * Compact price for cards: 12.8 میلیارد / 850 میلیون.
     * Not used for detail screens — those show full precision.
     */
    fun priceCompact(value: Long): String = when {
        value >= 1_000_000_000 -> {
            val billions = value / 1_000_000_000.0
            trimZeros(billions) + " میلیارد"
        }
        value >= 1_000_000 -> {
            val millions = value / 1_000_000.0
            trimZeros(millions) + " میلیون"
        }
        else -> price(value)
    }

    /** Price per square meter. */
    fun pricePerSqm(totalPrice: Long, areaSqm: Int): String? {
        if (areaSqm <= 0) return null
        return priceCompact(totalPrice / areaSqm) + " / متر"
    }

    fun area(sqm: Int): String = "${toPersianDigits(sqm)} متر"

    fun bedrooms(count: Int): String = when (count) {
        0 -> "بدون خواب"
        1 -> "یک خواب"
        2 -> "دو خواب"
        3 -> "سه خواب"
        else -> "${toPersianDigits(count)} خواب"
    }

    private fun trimZeros(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        val s = if (rounded == Math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
        return s.toPersianDigits()
    }
}

/** Default app locale constant — Persian (Iran). */
val APP_LOCALE: Locale = Locale("fa", "IR")
