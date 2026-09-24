package ir.chardivari.feature.auth

/**
 * Iranian mobile normalization to E.164 (+98…).
 * Accepts local 09…, international +98 / 0098 / 98 forms.
 */
object IranianPhone {
    fun normalizeToE164(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            // 09123456789
            digits.length == 11 && digits.startsWith("09") -> "+98${digits.drop(1)}"
            // 9123456789
            digits.length == 10 && digits.startsWith("9") -> "+98$digits"
            // 989123456789
            digits.length == 12 && digits.startsWith("989") -> "+$digits"
            // 00989123456789
            digits.length == 14 && digits.startsWith("00989") -> "+${digits.removePrefix("00")}"
            // 0989123456789 (rare)
            digits.length == 13 && digits.startsWith("0989") -> "+${digits.drop(1)}"
            else -> null
        }
    }

    fun isValidIranMobile(raw: String): Boolean = normalizeToE164(raw) != null
}
